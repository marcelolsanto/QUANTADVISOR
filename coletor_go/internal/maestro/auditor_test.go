package maestro

import (
	"math"
	"testing"
)

func TestCalcularKelly(t *testing.T) {
	testes := []struct {
		nome       string
		winRate    float64
		riskReward float64
		esperado   float64
	}{
		{
			nome:       "Cenário de Vantagem Clara (Deve Investir 40%)",
			winRate:    0.60,
			riskReward: 2.00,
			esperado:   0.40,
		},
		{
			nome:       "Cenário de Desvantagem Matemática (Não Investir)",
			winRate:    0.40,
			riskReward: 1.00,
			esperado:   0.00,
		},
		{
			nome:       "Proteção contra Divisão por Zero",
			winRate:    0.50,
			riskReward: 0.00,
			esperado:   0.00,
		},
	}

	for _, tt := range testes {
		t.Run(tt.nome, func(t *testing.T) {
			resultado := calcularKelly(tt.winRate, tt.riskReward)
			
			// 👇 A CORREÇÃO ESTÁ AQUI: Usando Tolerância (Epsilon de 1e-9)
			if math.Abs(resultado-tt.esperado) > 1e-9 {
				t.Errorf("Falha no %s: Esperado %.4f, mas o robô calculou %.4f", tt.nome, tt.esperado, resultado)
			}
		})
	}
}