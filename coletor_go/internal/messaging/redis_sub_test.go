package messaging_test

import (
	"encoding/json"
	"testing"

	"quantadvisor/internal/messaging"
)

func TestSignalPayload_Unmarshal(t *testing.T) {
	rawJSON := `{
		"strategy": "pairs_trading",
		"action": "SHORT_SPREAD",
		"asset_a": "AAPL",
		"asset_b": "MSFT",
		"target_qty": 1000,
		"timestamp": "2026-08-10T05:56:50Z"
	}`

	var signal messaging.SignalPayload
	err := json.Unmarshal([]byte(rawJSON), &signal)
	if err != nil {
		t.Fatalf("Erro ao decodificar JSON de sinal: %v", err)
	}

	if signal.Strategy != "pairs_trading" {
		t.Errorf("Estrategia incorreta: esperada 'pairs_trading', obtido '%s'", signal.Strategy)
	}

	if signal.Action != "SHORT_SPREAD" {
		t.Errorf("Acao incorreta: esperada 'SHORT_SPREAD', obtido '%s'", signal.Action)
	}

	if signal.AssetA != "AAPL" || signal.AssetB != "MSFT" {
		t.Errorf("Ativos incorretos: obtido %s / %s", signal.AssetA, signal.AssetB)
	}

	if signal.TargetQty != 1000 {
		t.Errorf("Quantidade incorreta: esperada 1000, obtido %d", signal.TargetQty)
	}
}
