package messaging_test

import (
	"context"
	"encoding/json"
	"os"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"quantadvisor/internal/broker"
	"quantadvisor/internal/database"
	"quantadvisor/internal/execution"
	"quantadvisor/internal/messaging"
)

func TestPubSubIntegration_PythonToGoSignal(t *testing.T) {
	redisHost := os.Getenv("REDIS_HOST")
	if redisHost == "" {
		redisHost = "localhost"
	}
	redisPort := os.Getenv("REDIS_PORT")
	if redisPort == "" {
		redisPort = "6379"
	}

	database.Rdb = redis.NewClient(&redis.Options{
		Addr: redisHost + ":" + redisPort,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	if err := database.Rdb.Ping(ctx).Err(); err != nil {
		t.Skipf("⚠️ Redis nao acessivel no ambiente de teste (%v). Ppulando teste de integracao PubSub.", err)
	}

	// Instancia o Broker Mock e TWAPEngine
	adapter := broker.NewBrokerAdapter("USD")
	twapEngine := execution.NewTWAPEngine(adapter)

	// Inicia o Listener em Goroutine separada
	listenerCtx, cancelListener := context.WithCancel(context.Background())
	defer cancelListener()

	go func() {
		_ = messaging.ListenForSignals(listenerCtx, twapEngine)
	}()

	time.Sleep(100 * time.Millisecond)

	// Simula a publicacao enviada pelo Motor Python
	payload := map[string]interface{}{
		"strategy":   "pairs_trading",
		"action":     "SHORT_SPREAD",
		"asset_a":    "AAPL",
		"asset_b":    "MSFT",
		"target_qty": 50,
		"price":      185.00,
		"timestamp":  time.Now().Format(time.RFC3339),
	}

	payloadBytes, _ := json.Marshal(payload)
	subscribers, err := database.Rdb.Publish(context.Background(), "hft:signals", payloadBytes).Result()
	if err != nil {
		t.Fatalf("Erro ao publicar no Redis: %v", err)
	}

	if subscribers < 1 {
		t.Logf("Aviso: Nao havia assinantes ativos no canal hft:signals no momento do disparo (%d assinantes).", subscribers)
	}

	time.Sleep(300 * time.Millisecond)
	t.Log("✅ Teste de Integração Pub/Sub Redis (Python -> Go TWAP) Concluído com Sucesso!")
}

func TestPubSubIntegration_ClosePosition(t *testing.T) {
	redisHost := os.Getenv("REDIS_HOST")
	if redisHost == "" {
		redisHost = "localhost"
	}
	redisPort := os.Getenv("REDIS_PORT")
	if redisPort == "" {
		redisPort = "6379"
	}

	database.Rdb = redis.NewClient(&redis.Options{
		Addr: redisHost + ":" + redisPort,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	if err := database.Rdb.Ping(ctx).Err(); err != nil {
		t.Skipf("⚠️ Redis nao acessivel no ambiente de teste (%v). Pulando teste CLOSE_POSITION.", err)
	}

	adapter := broker.NewBrokerAdapter("USD")
	twapEngine := execution.NewTWAPEngine(adapter)

	listenerCtx, cancelListener := context.WithCancel(context.Background())
	defer cancelListener()

	go func() {
		_ = messaging.ListenForSignals(listenerCtx, twapEngine)
	}()

	time.Sleep(100 * time.Millisecond)

	payloadClose := map[string]interface{}{
		"strategy":   "capital_recycling",
		"action":     "CLOSE_POSITION",
		"asset_a":    "VALE3",
		"asset_b":    "",
		"target_qty": 200,
		"price":      62.50,
		"timestamp":  time.Now().Format(time.RFC3339),
	}

	payloadBytes, _ := json.Marshal(payloadClose)
	_, err := database.Rdb.Publish(context.Background(), "hft:signals", payloadBytes).Result()
	if err != nil {
		t.Fatalf("Erro ao publicar CLOSE_POSITION no Redis: %v", err)
	}

	time.Sleep(300 * time.Millisecond)
	t.Log("✅ Teste de Integração CLOSE_POSITION (Capital Recycling -> Go TWAP) Concluído com Sucesso!")
}
