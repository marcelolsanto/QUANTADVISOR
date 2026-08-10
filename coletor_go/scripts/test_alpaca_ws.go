//go:build ignore

package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

// Carrega .env removendo aspas simples ou duplas
func carregarEnv(filepath string) {
	file, err := os.Open(filepath)
	if err != nil {
		return
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			key := strings.TrimSpace(parts[0])
			val := strings.Trim(strings.TrimSpace(parts[1]), "\"'")
			if os.Getenv(key) == "" {
				os.Setenv(key, val)
			}
		}
	}
}

func main() {
	log.Println("========================================================")
	log.Println("⚡ [ALPACA HFT ENGINE] Script de Validação de Market Data")
	log.Println("========================================================")

	carregarEnv("/workspace/.env")
	carregarEnv("../.env")
	carregarEnv(".env")

	apiKey := strings.Trim(os.Getenv("ALPACA_API_KEY"), "\"'")
	apiSecret := strings.Trim(os.Getenv("ALPACA_API_SECRET"), "\"'")

	if apiKey == "" || apiSecret == "" {
		log.Fatalf("❌ ALPACA_API_KEY ou ALPACA_API_SECRET não encontradas no arquivo .env")
	}

	log.Printf("🔑 Credenciais Detectadas | Key ID: %s... (Tam: %d) | Secret: ***%s (Tam: %d)",
		apiKey[:6], len(apiKey), apiSecret[len(apiSecret)-4:], len(apiSecret))

	// 1. Validação previa via REST API (Paper Trading Account)
	urlREST := "https://paper-api.alpaca.markets/v2/account"
	client := &http.Client{Timeout: 5 * time.Second}
	req, _ := http.NewRequest("GET", urlREST, nil)
	req.Header.Set("APCA-API-KEY-ID", apiKey)
	req.Header.Set("APCA-API-SECRET-KEY", apiSecret)

	respREST, errREST := client.Do(req)
	if errREST == nil && respREST != nil {
		body, _ := io.ReadAll(respREST.Body)
		respREST.Body.Close()
		if respREST.StatusCode == 200 {
			log.Printf("✅ Autenticação REST Alpaca: OK (HTTP 200)")
		} else {
			log.Printf("⚠️ Autenticação REST Alpaca retornou HTTP %d: %s", respREST.StatusCode, string(body))
		}
	}

	// 2. Conexão ao WebSocket IEX Stream
	wsURL := "wss://stream.data.alpaca.markets/v2/iex"
	log.Printf("🔌 Conectando ao WebSocket IEX Market Data: %s", wsURL)

	dialer := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, resp, err := dialer.Dial(wsURL, nil)
	if err != nil {
		if resp != nil {
			log.Fatalf("❌ Conexão WebSocket Rejeitada (HTTP Status %d): %v", resp.StatusCode, err)
		}
		log.Fatalf("❌ Falha de Rede na conexão WebSocket: %v", err)
	}
	defer conn.Close()

	// Leitura da mensagem de boas-vindas do servidor
	_, welcomeMsg, err := conn.ReadMessage()
	if err != nil {
		log.Fatalf("❌ Erro ao ler mensagem inicial do servidor Alpaca: %v", err)
	}
	log.Printf("📩 Servidor Alpaca: %s", string(welcomeMsg))

	// 3. Envio de Payload de Autenticação
	authPayload := map[string]string{
		"action": "auth",
		"key":    apiKey,
		"secret": apiSecret,
	}

	if err := conn.WriteJSON(authPayload); err != nil {
		log.Fatalf("❌ Falha ao enviar credenciais de autenticação: %v", err)
	}

	// Recebimento da resposta de autenticação
	_, authRespMsg, err := conn.ReadMessage()
	if err != nil {
		log.Fatalf("❌ Erro ao ler resposta de autenticação: %v", err)
	}
	log.Printf("🔑 Resposta de Autenticação WebSocket: %s", string(authRespMsg))

	if strings.Contains(string(authRespMsg), "auth failed") || strings.Contains(string(authRespMsg), "error") {
		log.Println("--------------------------------------------------------")
		log.Println("❌ ERRO DE AUTENTICAÇÃO NA ALPACA (code 402 / auth failed)")
		log.Println("💡 Diagnóstico HFT: As chaves API em .env foram recusadas pela Alpaca.")
		log.Println("   Por favor, verifique se a chave em https://app.alpaca.markets foi gerada no modo 'Paper Trading'")
		log.Println("   e se a Secret Key completa de 40 caracteres foi copiada sem cortes.")
		log.Println("--------------------------------------------------------")
		os.Exit(1)
	}

	// 4. Inscrição no Ticker AAPL (Quotes, Trades, Bars)
	subPayload := map[string]interface{}{
		"action": "subscribe",
		"trades": []string{"AAPL"},
		"quotes": []string{"AAPL"},
		"bars":   []string{"AAPL"},
	}

	if err := conn.WriteJSON(subPayload); err != nil {
		log.Fatalf("❌ Falha ao enviar requisição de inscrição em AAPL: %v", err)
	}

	_, subRespMsg, err := conn.ReadMessage()
	if err != nil {
		log.Fatalf("❌ Erro ao ler confirmação de assinatura: %v", err)
	}
	log.Printf("📡 Status da Assinatura: %s", string(subRespMsg))

	log.Println("\n========================================================")
	log.Println("🟢 FEED DE MERCADO AO VIVO ATIVO! Escutando AAPL por 10s...")
	log.Println("========================================================")

	inicio := time.Now()
	totalTicks := 0

	// Define o deadline de 12 segundos para cobrir toda a janela do teste
	conn.SetReadDeadline(time.Now().Add(12 * time.Second))

	for time.Since(inicio) < 10*time.Second {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			if strings.Contains(err.Error(), "timeout") || strings.Contains(err.Error(), "deadline") {
				log.Println("ℹ️ Janela de 10s encerrada (nenhum tick emitido no horário fora de pregão).")
				break
			}
			log.Printf("ℹ️ Conexão encerrada pelo servidor: %v", err)
			break
		}

		var ticks []map[string]interface{}
		if err := json.Unmarshal(msg, &ticks); err == nil {
			for _, tick := range ticks {
				totalTicks++
				msgType, _ := tick["T"].(string)
				symbol, _ := tick["S"].(string)
				tstamp, _ := tick["t"].(string)

				if msgType == "q" { // Quote
					bidPrice, _ := tick["bp"].(float64)
					bidSize, _ := tick["bs"].(float64)
					askPrice, _ := tick["ap"].(float64)
					askSize, _ := tick["as"].(float64)
					fmt.Printf("[%s] 📊 QUOTE %s | Bid: $%.2f (Qtd: %.0f) | Ask: $%.2f (Qtd: %.0f) | Hora: %s\n",
						time.Now().Format("15:04:05.000"), symbol, bidPrice, bidSize, askPrice, askSize, tstamp)
				} else if msgType == "t" { // Trade
					price, _ := tick["p"].(float64)
					size, _ := tick["s"].(float64)
					fmt.Printf("[%s] ⚡ TRADE %s | Preço Negociado: $%.2f | Volume: %.0f cotas | Hora: %s\n",
						time.Now().Format("15:04:05.000"), symbol, price, size, tstamp)
				} else if msgType == "b" { // Bar
					closePrice, _ := tick["c"].(float64)
					vol, _ := tick["v"].(float64)
					fmt.Printf("[%s] 📈 BAR   %s | Fechamento: $%.2f | Volume Minuto: %.0f | Hora: %s\n",
						time.Now().Format("15:04:05.000"), symbol, closePrice, vol, tstamp)
				} else {
					fmt.Printf("[%s] ℹ️ EVENTO %s: %v\n", time.Now().Format("15:04:05.000"), msgType, tick)
				}
			}
		}
	}

	log.Println("========================================================")
	log.Printf("🏁 TESTE CONCLUÍDO! Total de ticks recebidos em 10s: %d", totalTicks)
	log.Println("========================================================")
}
