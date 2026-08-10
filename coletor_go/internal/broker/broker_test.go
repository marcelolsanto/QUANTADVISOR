package broker_test

import (
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"quantadvisor/internal/broker"
	"quantadvisor/internal/models"
)

func TestNewBrokerAdapter_FactorySelection(t *testing.T) {
	// 1. Jurisdição USD -> Deve instanciar AlpacaAdapter
	adapterUSD := broker.NewBrokerAdapter("USD")
	if adapterUSD == nil {
		t.Fatal("Esperava uma instância de AlpacaAdapter para USD, obtido nil")
	}

	// 2. Jurisdição BRL -> Deve instanciar BTGAdapter
	adapterBRL := broker.NewBrokerAdapter("BRL")
	if adapterBRL == nil {
		t.Fatal("Esperava uma instância de BTGAdapter para BRL, obtido nil")
	}
}

func TestAlpacaAdapter_PlaceOrder(t *testing.T) {
	// Servidor mock da Alpaca REST API
	tsMock := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("APCA-API-KEY-ID") == "" || r.Header.Get("APCA-API-SECRET-KEY") == "" {
			w.WriteHeader(http.StatusUnauthorized)
			w.Write([]byte(`{"code": 40100000, "message": "unauthorized"}`))
			return
		}
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"id": "alpaca-ordem-12345", "status": "new", "symbol": "AAPL"}`))
	}))
	defer tsMock.Close()

	os.Setenv("ALPACA_REST_URL", tsMock.URL)
	os.Setenv("ALPACA_API_KEY", "test_key")
	os.Setenv("ALPACA_API_SECRET", "test_secret")

	alpaca := broker.NewAlpacaAdapter()
	ordemReq := models.OrdemRequest{
		UsuarioID:  1,
		Ticker:     "AAPL",
		TipoOrdem:  "COMPRA",
		Quantidade: 10,
		Preco:      185.50,
		Moeda:      "USD",
	}

	orderID, err := alpaca.PlaceOrder(ordemReq)
	if err != nil {
		t.Fatalf("Erro inesperado ao enviar ordem Alpaca: %v", err)
	}

	if orderID != "alpaca-ordem-12345" {
		t.Errorf("ID retornado incorreto. Esperava 'alpaca-ordem-12345', obtido '%s'", orderID)
	}
}

func TestBTGAdapter_PlaceOrder(t *testing.T) {
	// Servidor mock da BTG Pactual REST API
	tsMock := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-BTG-Client-ID") == "" || r.Header.Get("X-BTG-Client-Secret") == "" {
			w.WriteHeader(http.StatusUnauthorized)
			w.Write([]byte(`{"error": "credenciais invalidas"}`))
			return
		}
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"order_id": "btg-b3-98765", "status": "RECEIVED"}`))
	}))
	defer tsMock.Close()

	os.Setenv("BTG_API_URL", tsMock.URL)
	os.Setenv("BTG_CLIENT_ID", "btg_id_test")
	os.Setenv("BTG_CLIENT_SECRET", "btg_secret_test")

	btg := broker.NewBTGAdapter()
	ordemReq := models.OrdemRequest{
		UsuarioID:  1,
		Ticker:     "PETR4",
		TipoOrdem:  "COMPRA",
		Quantidade: 100,
		Preco:      38.50,
		Moeda:      "BRL",
	}

	orderID, err := btg.PlaceOrder(ordemReq)
	if err != nil {
		t.Fatalf("Erro inesperado ao enviar ordem BTG Pactual: %v", err)
	}

	if orderID != "btg-b3-98765" {
		t.Errorf("ID retornado incorreto. Esperava 'btg-b3-98765', obtido '%s'", orderID)
	}
}
