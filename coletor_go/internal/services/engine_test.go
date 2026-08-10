package services

import (
	"bytes"
	"io"
	"net/http"
	"testing"
)

type MockTransport struct {
	RoundTripFunc func(req *http.Request) (*http.Response, error)
}

func (m *MockTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	return m.RoundTripFunc(req)
}

func TestExtrairTaxaSelic(t *testing.T) {
	clienteOriginal := httpClient
	defer func() { httpClient = clienteOriginal }()

	httpClient = &http.Client{
		Transport: &MockTransport{
			RoundTripFunc: func(req *http.Request) (*http.Response, error) {
				urlEsperada := "https://api.bcb.gov.br/dados/serie/bcdata.sgs.432/dados/ultimos/1?formato=json"
				if req.URL.String() != urlEsperada {
					t.Errorf("O sistema tentou chamar a URL errada: %s", req.URL.String())
				}

				jsonFalso := `[{"data":"20/06/2026","valor":"12.75"}]`
				
				return &http.Response{
					StatusCode: 200,
					Body:       io.NopCloser(bytes.NewBufferString(jsonFalso)),
					Header:     make(http.Header),
				}, nil
			},
		},
	}

	extrairTaxaSelic()

	esperado := 0.1275
	if TaxaSelicGlobal != esperado {
		t.Errorf("Falha: Esperava que a SelicGlobal fosse %.4f, mas o sistema registrou %.4f", esperado, TaxaSelicGlobal)
	}
}

// ADICIONE ISTO NO FINAL DO ARQUIVO: coletor_go/internal/services/engine_test.go

func TestNormalizarMoeda(t *testing.T) {
	// Travamos a variável global do Dólar para garantir que o teste seja previsível
	valorDolarMock := 5.00 
	DolarGlobal = valorDolarMock

	testes := []struct {
		nome     string
		ticker   string
		preco    float64
		esperado float64 // O que o sistema DEVE devolver
	}{
		{
			nome:     "Ação Brasileira Padrão (Sem conversão)",
			ticker:   "PETR4",
			preco:    35.00,
			esperado: 35.00,
		},
		{
			nome:     "Ação Brasileira com sufixo SA (Sem conversão)",
			ticker:   "VALE3.SA",
			preco:    60.00,
			esperado: 60.00,
		},
		{
			nome:     "BDR na B3 (Termina em 34 ou 39 - Sem conversão)",
			ticker:   "AAPL34",
			preco:    40.00,
			esperado: 40.00,
		},
		{
			nome:     "Ação Americana Pura (Ticker só de letras - DEVE MULTIPLICAR PELO DÓLAR)",
			ticker:   "AAPL",
			preco:    100.00,
			esperado: 500.00, // 100 * 5.00
		},
		{
			nome:     "ETF Americano Puro (Ticker só de letras - DEVE MULTIPLICAR PELO DÓLAR)",
			ticker:   "SPY",
			preco:    500.00,
			esperado: 2500.00, // 500 * 5.00
		},
	}

	for _, tt := range testes {
		t.Run(tt.nome, func(t *testing.T) {
			resultado := normalizarMoeda(tt.ticker, tt.preco)
			
			if resultado != tt.esperado {
				t.Errorf("❌ Falha no '%s' (Ticker: %s) | Preço Original: %.2f | Esperado: R$ %.2f | O sistema retornou: R$ %.2f", 
					tt.nome, tt.ticker, tt.preco, tt.esperado, resultado)
			} else {
				t.Logf("✅ Passou: %s", tt.nome)
			}
		})
	}
}