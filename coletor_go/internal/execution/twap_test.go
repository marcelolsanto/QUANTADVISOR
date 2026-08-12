package execution_test

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"testing"

	"quantadvisor/internal/execution"
	"quantadvisor/internal/models"
)

type MockBrokerAdapter struct {
	mu           sync.Mutex
	ordersPlaced []models.OrdemRequest
	shouldFail   bool
}

func (m *MockBrokerAdapter) Connect() error {
	return nil
}

func (m *MockBrokerAdapter) SubscribeTicker(ticker string) error {
	return nil
}

func (m *MockBrokerAdapter) SubscribeTickers(tickers []string) error {
	return nil
}

func (m *MockBrokerAdapter) GetBuyingPower() (float64, error) {
	return 100000.0, nil
}

func (m *MockBrokerAdapter) PlaceOrder(req models.OrdemRequest) (string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.shouldFail {
		return "", errors.New("simulacao de erro na corretora")
	}

	m.ordersPlaced = append(m.ordersPlaced, req)
	orderID := fmt.Sprintf("mock-twap-ordem-%d", len(m.ordersPlaced))
	return orderID, nil
}

func TestExecuteTWAP_Sucesso(t *testing.T) {
	mockBroker := &MockBrokerAdapter{}
	engine := execution.NewTWAPEngine(mockBroker)

	ctx := context.Background()
	totalQty := 1000
	slices := 5
	durationMinutes := 1
	symbol := "AAPL"
	side := "COMPRA"
	price := 180.50

	orderIDs, err := engine.ExecuteTWAP(ctx, symbol, side, totalQty, durationMinutes, slices, price)
	if err != nil {
		t.Fatalf("Erro inesperado na execucao do TWAP: %v", err)
	}

	if len(orderIDs) != slices {
		t.Errorf("Esperava %d order IDs, obtido %d", slices, len(orderIDs))
	}

	mockBroker.mu.Lock()
	defer mockBroker.mu.Unlock()

	if len(mockBroker.ordersPlaced) != slices {
		t.Errorf("Esperava %d ordens enviadas ao broker, obtido %d", slices, len(mockBroker.ordersPlaced))
	}

	somaQuantidade := 0
	for _, req := range mockBroker.ordersPlaced {
		if req.Ticker != symbol {
			t.Errorf("Ticker incorreto na ordem. Esperava %s, obtido %s", symbol, req.Ticker)
		}
		if req.TipoOrdem != side {
			t.Errorf("Lado incorreto na ordem. Esperava %s, obtido %s", side, req.TipoOrdem)
		}
		somaQuantidade += req.Quantidade
	}

	if somaQuantidade != totalQty {
		t.Errorf("Soma das fatias (%d) diferente do totalQty esperado (%d)", somaQuantidade, totalQty)
	}
}

func TestExecuteTWAP_BrokerErro(t *testing.T) {
	mockBroker := &MockBrokerAdapter{shouldFail: true}
	engine := execution.NewTWAPEngine(mockBroker)

	ctx := context.Background()
	_, err := engine.ExecuteTWAP(ctx, "MSFT", "VENDA", 500, 1, 2, 400.00)
	if err == nil {
		t.Fatal("Esperava erro ao tentar executar TWAP com broker falhando, obtido nil")
	}
}
