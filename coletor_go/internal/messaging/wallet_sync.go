package messaging

import (
	"context"
	"fmt"
	"log"
	"time"

	"quantadvisor/internal/broker"
	"quantadvisor/internal/database"
)

// StartWalletSync executa um loop temporizado (time.Ticker),
// consulta o saldo/poder de compra (buying_power) da corretora via adapter.GetBuyingPower()
// e salva o valor no Redis na chave 'hft:wallet:buying_power'.
func StartWalletSync(ctx context.Context, adapter broker.BrokerAdapter, interval time.Duration) error {
	if database.Rdb == nil {
		return fmt.Errorf("conexao com o Redis nao foi inicializada em database.Rdb")
	}

	if adapter == nil {
		return fmt.Errorf("BrokerAdapter nao fornecido para o WalletSync")
	}

	if interval <= 0 {
		interval = 10 * time.Second
	}

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	log.Printf("💰 [WALLET SYNC HFT] Sincronizador de Saldo iniciado (Intervalo: %v)...", interval)

	// Executa a primeira sincronização imediatamente
	syncBalance(ctx, adapter)

	for {
		select {
		case <-ctx.Done():
			log.Println("🔌 [WALLET SYNC HFT] Sincronizador de Saldo encerrado (contexto cancelado).")
			return ctx.Err()
		case <-ticker.C:
			syncBalance(ctx, adapter)
		}
	}
}

func syncBalance(ctx context.Context, adapter broker.BrokerAdapter) {
	bp, err := adapter.GetBuyingPower()
	if err != nil {
		log.Printf("❌ [WALLET SYNC HFT] Erro ao consultar Buying Power: %v", err)
		return
	}

	redisKey := "hft:wallet:buying_power"
	err = database.Rdb.Set(ctx, redisKey, bp, 0).Err()
	if err != nil {
		log.Printf("❌ [WALLET SYNC HFT] Erro ao salvar '%s' no Redis: %v", redisKey, err)
		return
	}

	log.Printf("💾 [WALLET SYNC HFT] Saldo atualizado no Redis! Chave '%s' = US$ %.2f", redisKey, bp)
}
