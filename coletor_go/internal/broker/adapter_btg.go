package broker

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"

	"quantadvisor/internal/models"
	"quantadvisor/internal/services"
)

type BTGAdapter struct {
	clientID     string
	clientSecret string
	restURL      string
}

func NewBTGAdapter() *BTGAdapter {
	clientID := os.Getenv("BTG_CLIENT_ID")
	clientSecret := os.Getenv("BTG_CLIENT_SECRET")
	if clientID == "" {
		clientID = "btg_client_id_dev"
	}
	if clientSecret == "" {
		clientSecret = "btg_client_secret_dev"
	}

	restURL := os.Getenv("BTG_API_URL")
	if restURL == "" {
		restURL = "https://api.btgpactual.com/v1/orders"
	}

	return &BTGAdapter{
		clientID:     clientID,
		clientSecret: clientSecret,
		restURL:      restURL,
	}
}

func (b *BTGAdapter) Connect() error {
	log.Println("⚡ BTG Pactual Broker Adapter inicializado no modo REST API (B3 / BRL).")
	return nil
}

func (b *BTGAdapter) SubscribeTicker(ticker string) error {
	tickerUpper := strings.ToUpper(strings.TrimSpace(ticker))
	log.Printf("ℹ️ BTG Market Data via Streaming mantido em standby (utilizando Brapi/Go Engine para %s).", tickerUpper)
	return nil
}

func (b *BTGAdapter) PlaceOrder(req models.OrdemRequest) (string, error) {
	side := "BUY"
	if strings.ToUpper(req.TipoOrdem) == "VENDA" {
		side = "SELL"
	}

	btgPayload := map[string]interface{}{
		"account_id": req.UsuarioID,
		"symbol":     strings.ToUpper(req.Ticker),
		"side":       side,
		"quantity":   req.Quantidade,
		"price":      req.Preco,
		"order_type": "LIMIT",
		"market":     "B3",
		"currency":   "BRL",
	}

	bodyBytes, err := json.Marshal(btgPayload)
	if err != nil {
		return "", fmt.Errorf("falha ao codificar ordem BTG Pactual: %w", err)
	}

	httpReq, err := http.NewRequest("POST", b.restURL, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return "", fmt.Errorf("falha ao criar requisição BTG Pactual: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("X-BTG-Client-ID", b.clientID)
	httpReq.Header.Set("X-BTG-Client-Secret", b.clientSecret)

	resp, err := services.HTTPClient.Do(httpReq)
	if err != nil {
		return "", fmt.Errorf("erro de rede/timeout ao enviar ordem BTG Pactual: %w", err)
	}
	defer resp.Body.Close()

	respBytes, _ := io.ReadAll(resp.Body)

	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("erro na API BTG Pactual (HTTP %d): %s", resp.StatusCode, string(respBytes))
	}

	var resMap map[string]interface{}
	if err := json.Unmarshal(respBytes, &resMap); err == nil {
		if id, ok := resMap["order_id"].(string); ok {
			log.Printf("🚀 [BTG REST] Ordem enviada com sucesso para a B3! BTG Order ID: %s | Ticker: %s | Preço: R$%.2f", id, req.Ticker, req.Preco)
			return id, nil
		}
	}

	return string(respBytes), nil
}

func (b *BTGAdapter) GetBuyingPower() (float64, error) {
	log.Println("💰 [BTG REST] Margem de Compra B3 Consultada: R$ 500.000,00 (Simulada)")
	return 500000.0, nil
}
