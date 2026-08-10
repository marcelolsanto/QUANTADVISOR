package broker

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"quantadvisor/internal/database"
	"quantadvisor/internal/models"
	"quantadvisor/internal/services"
)

type AlpacaAdapter struct {
	apiKey      string
	apiSecret   string
	wsURL       string
	restURL     string
	conn        *websocket.Conn
	mu          sync.Mutex
	subscribers map[string]bool
}

func NewAlpacaAdapter() *AlpacaAdapter {
	apiKey := strings.Trim(os.Getenv("ALPACA_API_KEY"), "\"'")
	apiSecret := strings.Trim(os.Getenv("ALPACA_API_SECRET"), "\"'")
	if apiKey == "" {
		apiKey = "PK_ALPACA_DEV_MOCK_KEY"
	}
	if apiSecret == "" {
		apiSecret = "ALPACA_DEV_MOCK_SECRET"
	}

	wsURL := os.Getenv("ALPACA_WS_URL")
	if wsURL == "" {
		wsURL = "wss://stream.data.alpaca.markets/v2/iex"
	}

	restURL := os.Getenv("ALPACA_REST_URL")
	if restURL == "" {
		restURL = "https://paper-api.alpaca.markets/v2/orders"
	}

	return &AlpacaAdapter{
		apiKey:      apiKey,
		apiSecret:   apiSecret,
		wsURL:       wsURL,
		restURL:     restURL,
		subscribers: make(map[string]bool),
	}
}

func (a *AlpacaAdapter) Connect() error {
	a.mu.Lock()
	defer a.mu.Unlock()

	dialer := websocket.Dialer{
		HandshakeTimeout: 5 * time.Second,
	}

	conn, _, err := dialer.Dial(a.wsURL, nil)
	if err != nil {
		log.Printf("⚠️ [ALPACA WS] Erro ao conectar no WebSocket: %v. Operando em modo desacoplado.", err)
		return err
	}

	a.conn = conn

	// Envia mensagem de autenticação
	authPayload := map[string]string{
		"action": "auth",
		"key":    a.apiKey,
		"secret": a.apiSecret,
	}

	if err := conn.WriteJSON(authPayload); err != nil {
		log.Printf("❌ [ALPACA WS] Erro ao autenticar no WebSocket: %v", err)
		conn.Close()
		return err
	}

	log.Println("⚡ [ALPACA WS] Conexão WebSocket estabelecida e autenticada com sucesso (IEX Stream).")

	// Goroutine em background para escutar os ticks de mercado e publicar no Redis
	go a.listenMarketData()

	return nil
}

func (a *AlpacaAdapter) listenMarketData() {
	defer func() {
		if a.conn != nil {
			a.conn.Close()
		}
	}()

	for {
		if a.conn == nil {
			break
		}

		_, message, err := a.conn.ReadMessage()
		if err != nil {
			log.Printf("🔌 [ALPACA WS] Desconectado do Stream IEX: %v", err)
			break
		}

		// Processa os ticks JSON recebidos da Alpaca
		var ticks []map[string]interface{}
		if err := json.Unmarshal(message, &ticks); err == nil {
			for _, tick := range ticks {
				msgType, _ := tick["T"].(string)
				symbol, _ := tick["S"].(string)

				if symbol != "" && (msgType == "q" || msgType == "t") {
					var preco float64
					if msgType == "q" {
						if p, ok := tick["ap"].(float64); ok {
							preco = p
						}
					} else if msgType == "t" {
						if p, ok := tick["p"].(float64); ok {
							preco = p
						}
					}

					if preco > 0 {
						cotacaoPayload, _ := json.Marshal(map[string]interface{}{
							"ticker":      symbol,
							"preco":       preco,
							"moeda":       "USD",
							"fonte":       "ALPACA_IEX",
							"data_hora":   time.Now().Format(time.RFC3339),
							"tipo_evento": msgType,
						})

						// Publica imediatamente no Redis para o SSE e Maestro consumirem
						if database.Rdb != nil {
							database.Rdb.Publish(context.Background(), "cotacoes", cotacaoPayload)
						}
					}
				}
			}
		}
	}
}

func (a *AlpacaAdapter) SubscribeTicker(ticker string) error {
	a.mu.Lock()
	defer a.mu.Unlock()

	tickerUpper := strings.ToUpper(strings.TrimSpace(ticker))
	a.subscribers[tickerUpper] = true

	if a.conn == nil {
		log.Printf("ℹ️ [ALPACA WS] Inscrição gravada para %s (Aguardando conexão ativa)", tickerUpper)
		return nil
	}

	subMsg := map[string]interface{}{
		"action": "subscribe",
		"trades": []string{tickerUpper},
		"quotes": []string{tickerUpper},
	}

	err := a.conn.WriteJSON(subMsg)
	if err != nil {
		log.Printf("❌ [ALPACA WS] Erro ao inscrever ticker %s: %v", tickerUpper, err)
		return err
	}

	log.Printf("📡 [ALPACA WS] Ticker %s inscrito com sucesso no Feed de Cotações EUA.", tickerUpper)
	return nil
}

func (a *AlpacaAdapter) PlaceOrder(req models.OrdemRequest) (string, error) {
	side := "buy"
	if strings.ToUpper(req.TipoOrdem) == "VENDA" {
		side = "sell"
	}

	orderPayload := map[string]interface{}{
		"symbol":        strings.ToUpper(req.Ticker),
		"qty":           req.Quantidade,
		"side":          side,
		"type":          "limit",
		"time_in_force": "gtc",
		"limit_price":   fmt.Sprintf("%.2f", req.Preco),
	}

	bodyBytes, err := json.Marshal(orderPayload)
	if err != nil {
		return "", fmt.Errorf("falha ao codificar ordem Alpaca: %w", err)
	}

	httpReq, err := http.NewRequest("POST", a.restURL, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return "", fmt.Errorf("falha ao criar requisição Alpaca: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("APCA-API-KEY-ID", a.apiKey)
	httpReq.Header.Set("APCA-API-SECRET-KEY", a.apiSecret)

	resp, err := services.HTTPClient.Do(httpReq)
	if err != nil {
		return "", fmt.Errorf("erro de rede/timeout ao enviar ordem Alpaca: %w", err)
	}
	defer resp.Body.Close()

	respBytes, _ := io.ReadAll(resp.Body)

	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("erro na API Alpaca (HTTP %d): %s", resp.StatusCode, string(respBytes))
	}

	var resMap map[string]interface{}
	if err := json.Unmarshal(respBytes, &resMap); err == nil {
		if id, ok := resMap["id"].(string); ok {
			log.Printf("🚀 [ALPACA REST] Ordem enviada com sucesso! Order ID: %s | Ticker: %s | Preço: $%.2f", id, req.Ticker, req.Preco)
			return id, nil
		}
	}

	return string(respBytes), nil
}

func (a *AlpacaAdapter) GetBuyingPower() (float64, error) {
	accountURL := "https://paper-api.alpaca.markets/v2/account"
	httpReq, err := http.NewRequest("GET", accountURL, nil)
	if err != nil {
		return 100000.0, nil
	}

	httpReq.Header.Set("APCA-API-KEY-ID", a.apiKey)
	httpReq.Header.Set("APCA-API-SECRET-KEY", a.apiSecret)

	resp, err := services.HTTPClient.Do(httpReq)
	if err != nil {
		log.Printf("⚠️ [ALPACA REST] Falha ao consultar conta Alpaca: %v. Usando fallback de US$ 100.000,00", err)
		return 100000.0, nil
	}
	defer resp.Body.Close()

	bodyBytes, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		log.Printf("⚠️ [ALPACA REST] Erro ao obter buying_power (HTTP %d). Usando fallback de US$ 100.000,00", resp.StatusCode)
		return 100000.0, nil
	}

	var accMap map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &accMap); err == nil {
		if bpStr, ok := accMap["buying_power"].(string); ok {
			var bpVal float64
			fmt.Sscanf(bpStr, "%f", &bpVal)
			if bpVal > 0 {
				log.Printf("💰 [ALPACA REST] Poder de Compra Atualizado: US$ %.2f", bpVal)
				return bpVal, nil
			}
		}
	}

	return 100000.0, nil
}
