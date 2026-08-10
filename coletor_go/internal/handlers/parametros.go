package handlers

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"

	"quantadvisor/internal/database"
)

type ParametrosOperacionais struct {
	UsuarioID                  int     `json:"usuario_id"`
	MultiplicadorKelly         float64 `json:"multiplicador_kelly"`
	LimiteConcentracaoAtivo    float64 `json:"limite_concentracao_ativo"`
	GatilhoRebalanceamento     float64 `json:"gatilho_rebalanceamento"`
	MultiplicadorStopVar       float64 `json:"multiplicador_stop_var"`
	PisoMaxDrawdown            float64 `json:"piso_max_drawdown"`
	ZScoreCompraForte          float64 `json:"z_score_compra_forte"`
	ZScoreVendaLucro           float64 `json:"z_score_venda_lucro"` // 👈 NOVO
	ZScoreStopLoss             float64 `json:"z_score_stop_loss"`   // 👈 NOVO
	BloqueioSentimentoNegativo bool    `json:"bloqueio_sentimento_negativo"`
	CustoFriccaoPadrao         float64 `json:"custo_friccao_padrao"`
	ModoIsencaoFiscalEstrita   bool    `json:"modo_isencao_fiscal_estrita"`
}

// @Summary Obter Parâmetros de Risco
// @Description Retorna as configurações de Z-Score, Kelly e Travas de Risco do robô.
// @Tags 8. Parâmetros e Configurações
// @Security BearerAuth
// @Param usuario_id query int false "ID do Usuário"
// @Success 200 {object} ParametrosOperacionais
// @Router /parametros [get]
func HandlerGetParametros(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	uIDStr := r.URL.Query().Get("usuario_id")
	usuarioID, _ := strconv.Atoi(uIDStr)
	if usuarioID == 0 { usuarioID = 1 }

	var p ParametrosOperacionais
	query := `SELECT usuario_id, multiplicador_kelly, limite_concentracao_ativo, gatilho_rebalanceamento, multiplicador_stop_var, piso_max_drawdown, z_score_compra_forte, z_score_venda_lucro, z_score_stop_loss, bloqueio_sentimento_negativo, custo_friccao_padrao, modo_isencao_fiscal_estrita FROM parametros_operacionais WHERE usuario_id = $1`

	err := database.Conn.QueryRow(query, usuarioID).Scan(
		&p.UsuarioID, &p.MultiplicadorKelly, &p.LimiteConcentracaoAtivo, &p.GatilhoRebalanceamento,
		&p.MultiplicadorStopVar, &p.PisoMaxDrawdown, &p.ZScoreCompraForte,
		&p.ZScoreVendaLucro, &p.ZScoreStopLoss, 
		&p.BloqueioSentimentoNegativo, &p.CustoFriccaoPadrao, &p.ModoIsencaoFiscalEstrita,
	)

	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Parâmetros não encontrados"}`, http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(p)
}

// @Summary Salvar Parâmetros de Risco
// @Description Atualiza a sensibilidade e os limites de segurança da Inteligência Artificial.
// @Tags 8. Parâmetros e Configurações
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param request body ParametrosOperacionais true "Parâmetros"
// @Success 200 {object} map[string]interface{}
// @Router /parametros [post]
func HandlerUpdateParametros(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	var p ParametrosOperacionais
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	query := `
		UPDATE parametros_operacionais SET 
			multiplicador_kelly = $1, limite_concentracao_ativo = $2, gatilho_rebalanceamento = $3,
			multiplicador_stop_var = $4, piso_max_drawdown = $5, z_score_compra_forte = $6,
			z_score_venda_lucro = $7, z_score_stop_loss = $8,
			bloqueio_sentimento_negativo = $9, custo_friccao_padrao = $10, modo_isencao_fiscal_estrita = $11
		WHERE usuario_id = $12
	`
	_, err := database.Conn.Exec(query,
		p.MultiplicadorKelly, p.LimiteConcentracaoAtivo, p.GatilhoRebalanceamento,
		p.MultiplicadorStopVar, p.PisoMaxDrawdown, p.ZScoreCompraForte,
		p.ZScoreVendaLucro, p.ZScoreStopLoss,
		p.BloqueioSentimentoNegativo, p.CustoFriccaoPadrao, p.ModoIsencaoFiscalEstrita,
		p.UsuarioID,
	)

	if err != nil {
		log.Printf("⚠️ Erro ao atualizar parametros: %v", err)
		http.Error(w, `{"sucesso": false, "erro": "Erro ao salvar no banco de dados"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"sucesso": true, "mensagem": "Configurações do Motor atualizadas!"})
}