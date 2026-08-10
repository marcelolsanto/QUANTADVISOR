package messaging

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"quantadvisor/internal/database"
	"quantadvisor/internal/execution"
)

type SignalPayload struct {
	Strategy  string  `json:"strategy"`
	Action    string  `json:"action"`
	AssetA    string  `json:"asset_a"`
	AssetB    string  `json:"asset_b"`
	TargetQty int     `json:"target_qty"`
	Price     float64 `json:"price,omitempty"`
	Timestamp string  `json:"timestamp"`
}

// ListenForSignals abre uma subinscrição no canal Pub/Sub 'hft:signals' do Redis.
// Ao receber um sinal validado ("SHORT_SPREAD" ou "LONG_SPREAD"), dispara assincronamente
// a execução algorítmica TWAP através do twapEngine.
func ListenForSignals(ctx context.Context, twapEngine *execution.TWAPEngine) error {
	if database.Rdb == nil {
		return fmt.Errorf("conexao com o Redis nao foi inicializada em database.Rdb")
	}

	channel := "hft:signals"
	pubsub := database.Rdb.Subscribe(ctx, channel)
	defer pubsub.Close()

	log.Printf("📡 [REDIS SUB HFT] Escutando sinais quantitativos no canal '%s'...", channel)

	ch := pubsub.Channel()

	for {
		select {
		case <-ctx.Done():
			log.Println("🔌 [REDIS SUB HFT] Encerrando escuta de sinais HFT (contexto finalizado).")
			return ctx.Err()
		case msg, ok := <-ch:
			if !ok {
				log.Println("⚠️ [REDIS SUB HFT] Canal de subinscricao fechado pelo Redis.")
				return nil
			}

			var signal SignalPayload
			if err := json.Unmarshal([]byte(msg.Payload), &signal); err != nil {
				log.Printf("❌ [REDIS SUB HFT] Erro ao decodificar JSON de sinal: %v | Raw: %s", err, msg.Payload)
				continue
			}

			log.Printf(
				"⚡ [REDIS SUB HFT] Sinal Recebido! Estrategia: %s | Acao: %s | Ativo A: %s | Ativo B: %s | Qtd: %d | Time: %s",
				signal.Strategy, signal.Action, signal.AssetA, signal.AssetB, signal.TargetQty, signal.Timestamp,
			)

			if signal.Action == "SHORT_SPREAD" || signal.Action == "LONG_SPREAD" || signal.Action == "CLOSE_POSITION" {
				if twapEngine != nil {
					// Invocação assíncrona em goroutine para não bloquear a fila de escuta HFT
					go func(sig SignalPayload) {
						execSide := "COMPRA"
						targetSymbol := sig.AssetA
						if sig.Action == "SHORT_SPREAD" || sig.Action == "CLOSE_POSITION" {
							execSide = "VENDA"
						}

						targetQty := sig.TargetQty
						if targetQty <= 0 {
							targetQty = 500
						}

						price := sig.Price
						if price <= 0 {
							price = 150.00
						}

						execCtx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
						defer cancel()

						if sig.Action == "CLOSE_POSITION" {
							log.Printf("♻️ [CAPITAL RECYCLING HFT] Executando liquidacao da posicao mais fraca %s (%s)...", targetSymbol, execSide)
						} else {
							log.Printf("🚀 [HFT GATILHO TWAP] Disparando algoritmo TWAP em Goroutine Assincrona para %s (%s)...", targetSymbol, execSide)
						}

						_, err := twapEngine.ExecuteTWAP(execCtx, targetSymbol, execSide, targetQty, 1, 5, price)
						if err != nil {
							log.Printf("❌ [HFT GATILHO TWAP] Erro ao executar TWAP para %s: %v", targetSymbol, err)
						}
					}(signal)
				} else {
					log.Println("⚠️ [REDIS SUB HFT] TWAPEngine nao configurado. Sinal recebido mas nao executado.")
				}
			}
		}
	}
}
