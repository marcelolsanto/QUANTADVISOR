package execution

import (
	"context"
	"errors"
	"fmt"
	"log"
	"math"
	"strings"
	"time"

	"quantadvisor/internal/broker"
	"quantadvisor/internal/models"
)

type TWAPEngine struct {
	broker broker.BrokerAdapter
}

func NewTWAPEngine(adapter broker.BrokerAdapter) *TWAPEngine {
	return &TWAPEngine{
		broker: adapter,
	}
}

// ExecuteTWAP divide uma ordem grande em fatias temporais (slices) e as envia
// em intervalos regulares ao mercado atraves da interface BrokerAdapter.
func (e *TWAPEngine) ExecuteTWAP(
	ctx context.Context,
	symbol string,
	side string,
	totalQty int,
	durationMinutes int,
	slices int,
	price float64,
) ([]string, error) {

	if e.broker == nil {
		return nil, errors.New("BrokerAdapter nao configurado no TWAPEngine")
	}

	if totalQty <= 0 || slices <= 0 || durationMinutes <= 0 {
		return nil, fmt.Errorf("parametros invalidos para TWAP: totalQty=%d, slices=%d, durationMinutes=%d", totalQty, slices, durationMinutes)
	}

	symbolUpper := strings.ToUpper(strings.TrimSpace(symbol))
	sideUpper := strings.ToUpper(strings.TrimSpace(side))

	if slices > totalQty {
		slices = totalQty
	}

	baseQty := totalQty / slices
	remainder := totalQty % slices

	totalSeconds := float64(durationMinutes * 60)
	intervalSeconds := totalSeconds / float64(slices)
	intervalDuration := time.Duration(math.Max(1, intervalSeconds)) * time.Second

	log.Printf(
		"🚀 [TWAP ENGINE] Iniciando execucao algoritmica TWAP | Ticker: %s | Lote Total: %d | Fatias: %d | Duracao: %d min | Intervalo entre fatias: %v",
		symbolUpper, totalQty, slices, durationMinutes, intervalDuration,
	)

	orderIDs := make([]string, 0, slices)
	executedQty := 0

	for i := 0; i < slices; i++ {
		select {
		case <-ctx.Done():
			log.Printf("⚠️ [TWAP ENGINE] Execucao cancelada via contexto. Lote executado ate agora: %d/%d", executedQty, totalQty)
			return orderIDs, ctx.Err()
		default:
		}

		currentSliceQty := baseQty
		if i == 0 {
			currentSliceQty += remainder
		}

		req := models.OrdemRequest{
			UsuarioID:  1,
			Ticker:     symbolUpper,
			TipoOrdem:  sideUpper,
			Quantidade: currentSliceQty,
			Preco:      price,
			Moeda:      "USD",
		}

		orderID, err := e.broker.PlaceOrder(req)
		if err != nil {
			log.Printf("❌ [TWAP EXECUTION %d/%d] Erro ao enviar fatia: %v", i+1, slices, err)
			return orderIDs, fmt.Errorf("falha na fatia %d do TWAP: %w", i+1, err)
		}

		executedQty += currentSliceQty
		orderIDs = append(orderIDs, orderID)

		progressPct := (float64(executedQty) / float64(totalQty)) * 100.0
		log.Printf(
			"📊 [TWAP EXECUTION %d/%d] Order ID: %s | Ticker: %s | Fatia: %d cotas | Executado Acumulado: %d/%d (%.1f%%)",
			i+1, slices, orderID, symbolUpper, currentSliceQty, executedQty, totalQty, progressPct,
		)

		if i < slices-1 {
			select {
			case <-ctx.Done():
				return orderIDs, ctx.Err()
			case <-time.After(intervalDuration):
			}
		}
	}

	log.Printf("✅ [TWAP ENGINE] Algoritmo TWAP concluido com sucesso! Ticker: %s | 100%% Preenchido (%d cotas em %d ordens)", symbolUpper, totalQty, len(orderIDs))
	return orderIDs, nil
}
