package handlers

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"quantadvisor/internal/database"
)

type BuyingPowerResponse struct {
	BuyingPower float64 `json:"buying_power"`
	Currency    string  `json:"currency"`
	Timestamp   string  `json:"timestamp"`
}

type SignalHFTResponse struct {
	Strategy  string  `json:"strategy"`
	Action    string  `json:"action"`
	AssetA    string  `json:"asset_a"`
	AssetB    string  `json:"asset_b"`
	TargetQty float64 `json:"target_qty"`
	Price     float64 `json:"price,omitempty"`
	Timestamp string  `json:"timestamp"`
}

type TWAPExecutionResponse struct {
	OrderID     string  `json:"order_id"`
	Symbol      string  `json:"symbol"`
	Side        string  `json:"side"`
	Qty         float64 `json:"qty"`
	Price       float64 `json:"price"`
	SliceIndex  int     `json:"slice_index"`
	TotalSlices int     `json:"total_slices"`
	Status      string  `json:"status"`
	Timestamp   string  `json:"timestamp"`
}

// HandlerGetBuyingPower retorna o poder de compra sincronizado no Redis via Alpaca/BTG
// @Summary Buscar Poder de Compra (Buying Power)
// @Description Retorna o limite de margem e poder de compra atualizado via feedback loop Redis Alpaca/BTG
// @Tags Wallet & HFT
// @Produce json
// @Success 200 {object} BuyingPowerResponse
// @Router /wallet/buying-power [get]
func HandlerGetBuyingPower(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == http.MethodOptions {
		return
	}

	bpVal := 100000.0
	if database.Rdb != nil {
		val, err := database.Rdb.Get(context.Background(), "hft:wallet:buying_power").Float64()
		if err == nil && val > 0 {
			bpVal = val
		}
	}

	resp := BuyingPowerResponse{
		BuyingPower: bpVal,
		Currency:    "USD",
		Timestamp:   time.Now().Format(time.RFC3339),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

// HandlerGetSinaisHFT retorna o histórico recente de sinais de arbitragem e reciclagem de capital
// @Summary Listar Sinais HFT
// @Description Retorna a lista de sinais quantitativos emitidos pelo motor Python
// @Tags Wallet & HFT
// @Produce json
// @Success 200 {array} SignalHFTResponse
// @Router /hft/signals [get]
func HandlerGetSinaisHFT(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == http.MethodOptions {
		return
	}

	signals := []SignalHFTResponse{}
	if database.Rdb != nil {
		val, err := database.Rdb.Get(context.Background(), "hft:latest_signals").Result()
		if err == nil && val != "" {
			_ = json.Unmarshal([]byte(val), &signals)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(signals)
}

// HandlerGetExecucoesTWAP retorna as execuções de ordens fracionadas do TWAP Engine
// @Summary Histórico de Execuções TWAP
// @Description Retorna o registro de fatias de ordens executadas pelo TWAP Engine
// @Tags Wallet & HFT
// @Produce json
// @Success 200 {array} TWAPExecutionResponse
// @Router /hft/twap-history [get]
func HandlerGetExecucoesTWAP(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == http.MethodOptions {
		return
	}

	executions := []TWAPExecutionResponse{}
	if database.Rdb != nil {
		val, err := database.Rdb.Get(context.Background(), "hft:twap_executions").Result()
		if err == nil && val != "" {
			_ = json.Unmarshal([]byte(val), &executions)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(executions)
}
