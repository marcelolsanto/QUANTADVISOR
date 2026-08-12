package messaging_test

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"quantadvisor/internal/database"
	"quantadvisor/internal/messaging"
	"quantadvisor/internal/models"
)

type MockWalletBroker struct{}

func (m *MockWalletBroker) Connect() error                                { return nil }
func (m *MockWalletBroker) SubscribeTicker(ticker string) error          { return nil }
func (m *MockWalletBroker) SubscribeTickers(tickers []string) error       { return nil }
func (m *MockWalletBroker) PlaceOrder(req models.OrdemRequest) (string, error) { return "mock-id", nil }
func (m *MockWalletBroker) GetBuyingPower() (float64, error)              { return 150000.00, nil }

func TestWalletSync_Integration(t *testing.T) {
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

	ctxTest, cancelTest := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancelTest()

	if err := database.Rdb.Ping(ctxTest).Err(); err != nil {
		t.Skipf("⚠️ Redis nao acessivel (%v). Pulando teste de integracao do WalletSync.", err)
	}

	mockAdapter := &MockWalletBroker{}

	syncCtx, cancelSync := context.WithTimeout(context.Background(), 500*time.Millisecond)
	defer cancelSync()

	go func() {
		_ = messaging.StartWalletSync(syncCtx, mockAdapter, 50*time.Millisecond)
	}()

	time.Sleep(150 * time.Millisecond)

	val, err := database.Rdb.Get(context.Background(), "hft:wallet:buying_power").Float64()
	if err != nil {
		t.Fatalf("Erro ao ler chave hft:wallet:buying_power no Redis: %v", err)
	}

	if val != 150000.00 {
		t.Errorf("Valor retornado do Redis incorreto. Esperava 150000.00, obtido %.2f", val)
	}

	t.Logf("✅ WalletSync gravou com sucesso no Redis! hft:wallet:buying_power = US$ %.2f", val)
}
