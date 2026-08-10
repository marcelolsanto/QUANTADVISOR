package handlers_test

import (
	"testing"
	"time"

	"quantadvisor/internal/models"
)

// CalcularProcessamentoFIFO simula a lógica de baixa de lotes em ordens de venda FIFO
func CalcularProcessamentoFIFO(lotes []models.LoteFiscal, qtdVenda int, precoVenda float64) (lotesAtualizados []models.LoteFiscal, lucroRealizado float64, qtdAtendida int) {
	qtdRestante := qtdVenda
	lotesAtualizados = make([]models.LoteFiscal, len(lotes))
	copy(lotesAtualizados, lotes)

	for i := range lotesAtualizados {
		if qtdRestante <= 0 {
			break
		}
		if lotesAtualizados[i].QuantidadeAtual <= 0 {
			continue
		}

		qtdCasar := lotesAtualizados[i].QuantidadeAtual
		if qtdRestante < qtdCasar {
			qtdCasar = qtdRestante
		}

		custoLote := float64(qtdCasar) * lotesAtualizados[i].PrecoCompra
		receitaLote := float64(qtdCasar) * precoVenda
		lucroRealizado += (receitaLote - custoLote)

		lotesAtualizados[i].QuantidadeAtual -= qtdCasar
		qtdRestante -= qtdCasar
	}

	qtdAtendida = qtdVenda - qtdRestante
	return lotesAtualizados, lucroRealizado, qtdAtendida
}

func TestCalculoLotesFIFO_ExecucaoParcial(t *testing.T) {
	dataBase := time.Now().AddDate(0, 0, -10)
	lotesIniciais := []models.LoteFiscal{
		{ID: 1, Ticker: "PETR4", DataEntrada: dataBase, QuantidadeInicial: 100, QuantidadeAtual: 100, PrecoCompra: 30.00},
		{ID: 2, Ticker: "PETR4", DataEntrada: dataBase.AddDate(0, 0, 2), QuantidadeInicial: 100, QuantidadeAtual: 100, PrecoCompra: 35.00},
	}

	// Venda de 150 ações a R$ 40.00
	// Esperado:
	// - Lote 1: consumido totalmente (100 ações, lucro = (40-30)*100 = 1000)
	// - Lote 2: consumido parcialmente (50 ações, lucro = (40-35)*50 = 250)
	// Total Lucro Esperado = 1250.00
	lotesPos, lucro, qtdAtendida := CalcularProcessamentoFIFO(lotesIniciais, 150, 40.00)

	if qtdAtendida != 150 {
		t.Errorf("Esperado atender 150 ações, mas atendeu %d", qtdAtendida)
	}

	if lotesPos[0].QuantidadeAtual != 0 {
		t.Errorf("Lote 1 deveria estar zerado, mas restam %d", lotesPos[0].QuantidadeAtual)
	}

	if lotesPos[1].QuantidadeAtual != 50 {
		t.Errorf("Lote 2 deveria ter 50 ações, mas restam %d", lotesPos[1].QuantidadeAtual)
	}

	if lucro != 1250.00 {
		t.Errorf("Lucro esperado: R$ 1250.00, obtido: R$ %.2f", lucro)
	}
}

func TestCalculoLotesFIFO_QuantidadeInsuficiente(t *testing.T) {
	dataBase := time.Now().AddDate(0, 0, -5)
	lotesIniciais := []models.LoteFiscal{
		{ID: 1, Ticker: "VALE3", DataEntrada: dataBase, QuantidadeInicial: 50, QuantidadeAtual: 50, PrecoCompra: 60.00},
	}

	// Venda de 100 ações (temos apenas 50)
	lotesPos, lucro, qtdAtendida := CalcularProcessamentoFIFO(lotesIniciais, 100, 70.00)

	if qtdAtendida != 50 {
		t.Errorf("Esperado atender no máximo 50 ações, atendeu %d", qtdAtendida)
	}

	if lotesPos[0].QuantidadeAtual != 0 {
		t.Errorf("Lote 1 deveria estar zerado")
	}

	lucroEsperado := (70.00 - 60.00) * 50.0
	if lucro != lucroEsperado {
		t.Errorf("Lucro obtido R$ %.2f diferente do esperado R$ %.2f", lucro, lucroEsperado)
	}
}

func TestCalculoLotesFIFO_Prejuizo(t *testing.T) {
	dataBase := time.Now().AddDate(0, 0, -1)
	lotesIniciais := []models.LoteFiscal{
		{ID: 1, Ticker: "ITUB4", DataEntrada: dataBase, QuantidadeInicial: 200, QuantidadeAtual: 200, PrecoCompra: 30.00},
	}

	// Venda de 100 ações a R$ 25.00 (prejuízo)
	_, lucro, qtdAtendida := CalcularProcessamentoFIFO(lotesIniciais, 100, 25.00)

	if qtdAtendida != 100 {
		t.Errorf("Esperado atender 100 ações, atendeu %d", qtdAtendida)
	}

	prejuizoEsperado := -500.00 // (25-30)*100
	if lucro != prejuizoEsperado {
		t.Errorf("Prejuízo esperado: R$ %.2f, obtido: R$ %.2f", prejuizoEsperado, lucro)
	}
}
