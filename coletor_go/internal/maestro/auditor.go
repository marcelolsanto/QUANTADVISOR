package maestro

import (
	"log"
	"time"
	
	// Importe o pacote de risco recém-criado
	"quantadvisor/internal/risk" 
)

// AvaliarEExecutarOrdem atua como o filtro final antes da emissão da ordem para a corretora
func AvaliarEExecutarOrdem(ativo string, preco float64, sinalPPO string, winRate float64, riskReward float64) {
	
	// 1. GATEKEEPER: Se o ativo estiver em cooldown, retorna imediatamente (fim do spam)
	if risk.GlobalCooldown.EmCooldown(ativo) {
		return 
	}

	// 2. Processa apenas se o sinal for relevante
	if sinalPPO == "COMPRA FORTE" {
		
		// 3. LOG DE AUDITORIA: Isso vai revelar por que o cálculo do Kelly está resultando em 0.00
		log.Printf("🔍 DEBUG Kelly [%s] -> WinRate(p): %.4f | Risk/Reward(b): %.4f", ativo, winRate, riskReward)

		// 4. Cálculo de Risco
		fatorKelly := calcularKelly(winRate, riskReward)

		// 5. Bloqueio de Risco: Se não houver margem estatística, bloqueia o ativo
		if fatorKelly <= 0.00 {
			log.Printf("🛑 [RISCO] Compra de %s abortada. Kelly (%.2f%%) indica risco desproporcional.", ativo, fatorKelly)
			
			// Aplica um bloqueio de 5 minutos. Ajuste o time.Minute conforme o ciclo da sua estratégia.
			risk.GlobalCooldown.Bloquear(ativo, 5*time.Minute)
			return
		}

		// 6. Execução da Ordem (Com o bug de formatação %!(EXTRA) corrigido)
		quantidade := 1 // Substitua pela sua lógica de lotes (100 para ações B3, 1 para fracionário)
		log.Printf("⚖️ ORDEM EXECUTADA: COMPRA | %s | Qtd: %d | R$ %.2f", ativo, quantidade, preco)
		
		// -> SUA FUNÇÃO DE INTEGRAÇÃO COM A CORRETORA AQUI <-
	}
}

// calcularKelly aplica a fórmula de Kelly fracionário
func calcularKelly(p float64, b float64) float64 {
	// Proteção contra divisão por zero ou b negativo
	if b <= 0 {
		return 0.0
	}
	
	q := 1.0 - p // Probabilidade de perda
	kelly := ((p * b) - q) / b
	
	// Se a vantagem for negativa, a recomendação é não investir (0.00)
	if kelly < 0 {
		return 0.0
	}
	
	return kelly
}