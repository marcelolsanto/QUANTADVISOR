package handlers

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"time"

	"quantadvisor/internal/database"
	"quantadvisor/internal/services" // 👈 Import para ler o Dólar da memória RAM
)

type ResumoEstrategia struct {
	CapitalInicial  float64 `json:"capital_inicial"`
	CapitalAtualNet float64 `json:"capital_atual_net"`
	LucroLiquido    float64 `json:"lucro_liquido_net"`
	MaxDrawdown     float64 `json:"max_drawdown"`
	TotalOperacoes  int     `json:"total_operacoes"`
	WinRateNet      float64 `json:"win_rate_net"`
	CotacaoDolar    float64 `json:"cotacao_dolar"` // 👈 NOVA LINHA
}

type PontoCurvaCapital struct {
	Timestamp      string  `json:"timestamp"`
	PatrimonioNet  float64 `json:"patrimonio_net"`
	Volatilidade   float64 `json:"volatilidade_mercado"`
}

type ReplayDecisao struct {
	Timestamp      string  `json:"timestamp"`
	Ativo          string  `json:"ativo"`
	ZScore         float64 `json:"z_score"`
	FatorKelly     float64 `json:"fator_kelly_alocado"`
	AcaoExecutada  string  `json:"acao_executada"`
	CustoEstimado  float64 `json:"custo_friccao_bps"`
	PerfilRisco    string  `json:"perfil_risco"`
	RegimeMercado  string  `json:"regime_mercado"`
}

// @Summary Resumo Institucional (Tearsheet)
// @Description Retorna métricas B2B de performance (AUM, Sharpe, Drawdown).
// @Tags 6. Institucional (B2B)
// @Security BearerAuth
// @Param usuario_id query int false "ID do Usuário"
// @Success 200 {object} map[string]interface{}
// @Router /institucional/resumo [get]
func HandlerResumoEstrategia(w http.ResponseWriter, r *http.Request) {
	uIDStr := r.URL.Query().Get("usuario_id")
	usuarioID, _ := strconv.Atoi(uIDStr)
	if usuarioID == 0 { usuarioID = 1 }

	var resumo ResumoEstrategia
	
	query := `
	WITH Baseline AS (
		SELECT COALESCE(SUM(patrimonio_total), 0) as capital_base
		FROM historico_patrimonial
		WHERE usuario_id = $1 AND data_fechamento = (SELECT MIN(data_fechamento) FROM historico_patrimonial WHERE usuario_id = $1)
	),
	CapitalHistorico AS (
		SELECT 
			data_fechamento, 
			(SELECT capital_base FROM Baseline) + SUM(lucro_diario) as patrimonio_liquido,
			MAX((SELECT capital_base FROM Baseline) + SUM(lucro_diario)) OVER (ORDER BY data_fechamento) as pico_historico
		FROM historico_patrimonial
		WHERE usuario_id = $1
		GROUP BY data_fechamento
	)
	SELECT 
		COALESCE((SELECT patrimonio_liquido FROM CapitalHistorico ORDER BY data_fechamento ASC LIMIT 1), 0) as capital_inicial,
		COALESCE((SELECT patrimonio_liquido FROM CapitalHistorico ORDER BY data_fechamento DESC LIMIT 1), 0) as capital_atual,
		COALESCE((SELECT MIN((patrimonio_liquido - pico_historico) / NULLIF(pico_historico, 0)) * 100 FROM CapitalHistorico), 0) as max_drawdown;
	`
	err := database.Conn.QueryRow(query, usuarioID).Scan(&resumo.CapitalInicial, &resumo.CapitalAtualNet, &resumo.MaxDrawdown)
	if err != nil {
		log.Printf("Erro DB em HandlerResumoEstrategia: %v", err)
		http.Error(w, "Erro interno", http.StatusInternalServerError)
		return
	}
	resumo.LucroLiquido = resumo.CapitalAtualNet - resumo.CapitalInicial
	
	errCount := database.Conn.QueryRow("SELECT COUNT(id) FROM ordens_executadas WHERE usuario_id = $1", usuarioID).Scan(&resumo.TotalOperacoes)
	if errCount != nil {
		log.Printf("Aviso: Erro ao contar operações do usuario %d: %v", usuarioID, errCount)
	}

	// 👇 INJETA O DÓLAR AO VIVO AQUI 👇
	resumo.CotacaoDolar = services.DolarGlobal

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resumo)
}

// @Summary Curva de Capital
// @Description Retorna a evolução patrimonial e a volatilidade do mercado para o Tearsheet.
// @Tags 6. Institucional (B2B)
// @Security BearerAuth
// @Param usuario_id query int false "ID do Usuário"
// @Success 200 {array} map[string]interface{}
// @Router /institucional/curva-capital [get]
func HandlerCurvaCapital(w http.ResponseWriter, r *http.Request) {
	uIDStr := r.URL.Query().Get("usuario_id")
	usuarioID, _ := strconv.Atoi(uIDStr)
	if usuarioID == 0 { usuarioID = 1 }

	query := `
		WITH Baseline AS (
			SELECT COALESCE(SUM(patrimonio_total), 0) as capital_base FROM historico_patrimonial
			WHERE usuario_id = $1 AND data_fechamento = (SELECT MIN(data_fechamento) FROM historico_patrimonial WHERE usuario_id = $1)
		)
		SELECT 
			DATE_TRUNC('day', h.data_fechamento) as dia,
			(SELECT capital_base FROM Baseline) + SUM(h.lucro_diario) as patrimonio_net,
			COALESCE(STDDEV(SUM(h.patrimonio_total)) OVER (ORDER BY DATE_TRUNC('day', h.data_fechamento) ROWS BETWEEN 20 PRECEDING AND CURRENT ROW) / NULLIF(AVG(SUM(h.patrimonio_total)) OVER (ORDER BY DATE_TRUNC('day', h.data_fechamento) ROWS BETWEEN 20 PRECEDING AND CURRENT ROW), 0), 0) as volat_media
		FROM historico_patrimonial h 
		WHERE h.usuario_id = $1
		GROUP BY dia ORDER BY dia ASC
	`
	rows, err := database.Conn.Query(query, usuarioID)
	if err != nil {
		log.Printf("Erro DB em HandlerCurvaCapital: %v", err)
		http.Error(w, "Erro ao buscar curva", http.StatusInternalServerError)
		return
	}
	defer rows.Close()
	
	var curva []PontoCurvaCapital
	for rows.Next() {
		var p PontoCurvaCapital
		var t time.Time
		if err := rows.Scan(&t, &p.PatrimonioNet, &p.Volatilidade); err == nil {
			p.Timestamp = t.Format("2006-01-02")
			curva = append(curva, p)
		}
	}
	
	if curva == nil { 
		curva = []PontoCurvaCapital{} 
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(curva)
}

// @Summary Replay de Decisão da IA
// @Description Retorna os logs de auditoria imutáveis (Z-Score, Kelly, Fricção) do robô.
// @Tags 6. Institucional (B2B)
// @Security BearerAuth
// @Param usuario_id query int false "ID do Usuário"
// @Success 200 {array} map[string]interface{}
// @Router /institucional/replay [get]
func HandlerReplayDecisao(w http.ResponseWriter, r *http.Request) {
	uIDStr := r.URL.Query().Get("usuario_id")
	usuarioID, _ := strconv.Atoi(uIDStr)
	if usuarioID == 0 { usuarioID = 1 }

	query := `
		SELECT 
			o.data_hora, 
			o.ticker, 
			COALESCE(h.z_score_calculado, 0.0) as z_score_momento, 
			LEAST(COALESCE((o.quantidade * o.preco_execucao) / NULLIF((SELECT saldo_brl + (saldo_usd * 5.08) + COALESCE((SELECT SUM(quantidade_total * preco_medio) FROM posicoes_carteira WHERE usuario_id = o.usuario_id), 0) FROM contas_virtuais WHERE usuario_id = o.usuario_id), 0), 0.15), 1.0) as fator_kelly_aplicado, 
			o.tipo_ordem, 
			LEAST(COALESCE(ABS((o.preco_execucao - h.preco_analisado) / NULLIF(h.preco_analisado, 0)) + 0.0003, 0.0003), 0.02) as custo_taxas_bps,
			c.perfil_risco,
			COALESCE(o.regime_mercado, 'DESCONHECIDO') as regime_mercado
		FROM ordens_executadas o
		JOIN contas_virtuais c ON o.usuario_id = c.usuario_id
		LEFT JOIN LATERAL (
			SELECT z_score_calculado, preco_analisado FROM historico_recomendacoes hr 
			WHERE hr.ticker_ativo = o.ticker AND hr.data_hora <= o.data_hora 
			ORDER BY hr.data_hora DESC LIMIT 1
		) h ON true
		WHERE o.usuario_id = $1
		ORDER BY o.data_hora DESC 
		LIMIT 1000 
	`
	rows, err := database.Conn.Query(query, usuarioID)
	if err != nil {
		log.Printf("Erro DB em HandlerReplayDecisao: %v", err)
		http.Error(w, "Erro ao buscar replay", http.StatusInternalServerError)
		return
	}
	defer rows.Close()
	
	var historico []ReplayDecisao
	for rows.Next() {
		var h ReplayDecisao
		var t time.Time
		
		if err := rows.Scan(&t, &h.Ativo, &h.ZScore, &h.FatorKelly, &h.AcaoExecutada, &h.CustoEstimado, &h.PerfilRisco, &h.RegimeMercado); err == nil {
			h.Timestamp = t.Format("2006-01-02 15:04:05")
			historico = append(historico, h)
		} else {
			log.Printf("Erro ao escanear linha de auditoria: %v", err)
		}
	}
	
	if historico == nil { 
		historico = []ReplayDecisao{} 
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(historico)
}