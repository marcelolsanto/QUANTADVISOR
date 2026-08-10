package models
import (
	"time"
	"github.com/dgrijalva/jwt-go"
)

type OrdemRequest struct {
	UsuarioID         int     `json:"usuario_id"`
	Ticker            string  `json:"ticker"`
	TipoOrdem         string  `json:"tipo_ordem"`
	Quantidade        int     `json:"quantidade"`
	Preco             float64 `json:"preco"`
	Moeda             string  `json:"moeda"`              
	TaxaCambioMomento float64 `json:"taxa_cambio_momento"`
	VolumeBRL         float64 `json:"volume_brl"`          
}

type CarrinhoItem struct {
    ID     int     `json:"id"`
    Ticker string  `json:"ticker"`
    Tipo   string  `json:"tipo"`
    Qtd    int     `json:"quantidade"`
    Preco  float64 `json:"preco"`
}

type LimparCarrinhoReq struct {
    IDs []int `json:"ids"`
}

type Posicao struct {
	Ticker                  string   `json:"ticker"`
	Quantidade              int      `json:"quantidade"`
	PrecoMedio              float64  `json:"preco_medio"`
	Moeda                   string   `json:"moeda"`
	PrecoAtual              *float64 `json:"preco_atual"`
	StatusCotacao           string   `json:"status_cotacao"`
	
	// 👇 AS DUAS NOVAS VARIÁVEIS PARA A MARCAÇÃO A MERCADO (MtM) 👇
	LucroPrejuizoFinanceiro float64  `json:"lucro_prejuizo_financeiro"`
	LucroPrejuizoPercentual float64  `json:"lucro_prejuizo_percentual"`
}

type ItemLancamento struct {
    ID         int     `json:"id"`
    Debito     string  `json:"conta_debito"`
    Credito    string  `json:"conta_credito"`
    Valor      float64 `json:"valor"`
    Historico  string  `json:"historico"`
    Data       string  `json:"data_lancamento"`
    Liquidacao string  `json:"data_liquidacao"`
}

type CarteiraResponse struct {
	Sucesso     bool      `json:"sucesso"`
	NomeCliente string    `json:"nome_cliente"`
	SaldoBRL    float64   `json:"saldo_brl"` // 👈 Atualizado
	SaldoUSD    float64   `json:"saldo_usd"` // 👈 Novo
	Posicoes    []Posicao `json:"posicoes"`
}

type UsuarioResumo struct {
	ID               int       `json:"id"`
	Nome             string    `json:"nome"`
	Perfil           string    `json:"perfil_risco"`
	SaldoBRL         float64   `json:"saldo_brl"`
	SaldoUSD         float64   `json:"saldo_usd"`
	Email            string    `json:"email"`
	Whatsapp         string    `json:"whatsapp"`
	Login            string    `json:"login"`
	Lucro            float64   `json:"lucro_acumulado"`
	Role             string    `json:"role"`
	DataCadastro     time.Time `json:"data_cadastro"`
	PilotoAutomatico bool      `json:"piloto_automatico"`
}

// Adicione esta nova estrutura:
type NovaContaRequest struct {
	NomeCliente      string  `json:"nome_cliente"`
	PerfilRisco      string  `json:"perfil_risco"`
	SaldoInicial     float64 `json:"saldo_inicial"`
	SaldoUSD         float64 `json:"saldo_usd"`
	Email            string  `json:"email"`
	Whatsapp         string  `json:"whatsapp"`
	Login            string  `json:"login"`
	Senha            string  `json:"senha"`
	Role             string  `json:"role"`              // ✨ AGORA ELE LÊ A ROLE
	PilotoAutomatico bool    `json:"piloto_automatico"`
}

// Estrutura para Editar (Update)
type EditarContaRequest struct {
	ID               int    `json:"id"`
	NomeCliente      string `json:"nome_cliente"`
	PerfilRisco      string `json:"perfil_risco"`
	SaldoInicial     float64 `json:"saldo_inicial"`
	SaldoUSD         float64 `json:"saldo_usd"`
	Email            string `json:"email"`
	Whatsapp         string `json:"whatsapp"`
	Login            string `json:"login"`
	Senha            string `json:"senha"`
	Role             string `json:"role"`              // ✨ AGORA ELE LÊ A ROLE
	PilotoAutomatico bool   `json:"piloto_automatico"`
}

type DeletarContaRequest struct {
	ID int `json:"id"`
}

type OrdemExecutada struct {
	ID            int       `json:"id"`
	Ticker        string    `json:"ticker"`
	TipoOrdem     string    `json:"tipo_ordem"`
	Quantidade    int       `json:"quantidade"`
	PrecoExecucao float64   `json:"preco_execucao"`
	DataHora      time.Time `json:"data_hora"`
}

type PerfilInvestidor struct {
	ID                   int    `json:"id"`
	NomeUsuario          string `json:"nome_usuario"`
	PerfilComportamental string `json:"perfil_comportamental"`
}

type RiscoRequestPython struct {
	Tickers []string `json:"tickers"`
}

type RiscoResponsePython struct {
	Sucesso               bool                   `json:"sucesso"`
	AtivosAnalisados      int                    `json:"ativos_analisados"`
	AlertasConcentracao   []map[string]interface{} `json:"alertas_concentracao"`
	OportunidadesHedge    []map[string]interface{} `json:"oportunidades_hedge"`
	HeatmapBase64         string                 `json:"heatmap_base64"`
	Erro                  string                 `json:"erro,omitempty"`
}

type PythonResponse struct {
	Sucesso           bool    `json:"sucesso"`
	Ticker            string  `json:"ticker"`
	Sinal             string  `json:"sinal"`
	PrecoAtual        float64 `json:"preco_atual"`
	ZScore            float64 `json:"z_score"`
	KellyRecomendado  float64 `json:"kelly_recomendado"` 
	RiscoVar          float64 `json:"risco_var"` 
	DistanciaVwapPerc float64 `json:"distancia_vwap_perc"` // 👈 ADICIONE AQUI
	VolumeZScore      float64 `json:"volume_zscore"`
}

type ResultadoCalculoPython struct {
	Sucesso           bool              `json:"sucesso"`
	Ticker            string            `json:"ticker"`
	PrecoAtual        float64           `json:"preco_atual"`
	ZScore            float64           `json:"z_score"`
	RiscoVar          float64           `json:"risco_var"`
	DistanciaVwapPerc float64           `json:"distancia_vwap_perc"` // 👈 ADICIONE AQUI
	VolumeZScore      float64           `json:"volume_zscore"`
	KellyRecomendado  float64           `json:"kelly_recomendado"` 
	Sinal             string            `json:"sinal"`          
	SinaisPerfil      map[string]string `json:"sinais_perfil"` 
	Classe            string            `json:"classe"`
	Fonte             string            `json:"fonte"`
	HistoricoPrecos   []float64         `json:"historico_precos"`
}

// Estrutura para ler o JSON leve do Yahoo Finance
type YahooQuoteResponse struct {
	QuoteResponse struct {
		Result []struct {
			Symbol             string  `json:"symbol"`
			RegularMarketPrice float64 `json:"regularMarketPrice"`
		} `json:"result"`
		Error interface{} `json:"error"`
	} `json:"quoteResponse"`
}

// TogglePilotoReq recebe a ordem de ligar/desligar o piloto automático
type TogglePilotoReq struct {
	UsuarioID int  `json:"usuario_id"`
	Estado    bool `json:"estado"`
}

type YahooChartResponse struct {
	Chart struct {
		Result []struct {
			Meta struct {
				RegularMarketPrice float64 `json:"regularMarketPrice"`
			} `json:"meta"`
		} `json:"result"`
		Error interface{} `json:"error"`
	} `json:"chart"`
}

// Estruturas para ler o RSS do Yahoo Finance
type RSS struct {
	Channel Channel `xml:"channel"`
}
type Channel struct {
	Items []Item `xml:"item"`
}
type Item struct {
	Title string `xml:"title"`
}

// 1. Estrutura que o Go devolverá pronta para o React
type ResumoFiscalMensal struct {
	AnoMes                   string  `json:"ano_mes"`
	VolumeVendasSwing        float64 `json:"volume_vendas_swing"`
	LucroRealizadoSwing      float64 `json:"lucro_realizado_swing"`
	VolumeVendasExterior     float64 `json:"volume_vendas_exterior"`
	LucroRealizadoExterior   float64 `json:"lucro_realizado_exterior"`
	LucroRealizadoDayTrade   float64 `json:"lucro_realizado_daytrade"`
	IrrfDedoDuro             float64 `json:"irrf_dedo_duro_retido"`
	
	PrejuizoAnteriorSwing    float64 `json:"prejuizo_anterior_swing"`
	PrejuizoAnteriorExterior float64 `json:"prejuizo_anterior_exterior"`
	PrejuizoAnteriorDT       float64 `json:"prejuizo_anterior_dt"`
	
	BaseCalculoSwing         float64 `json:"base_calculo_swing"`
	BaseCalculoExterior      float64 `json:"base_calculo_exterior"`
	BaseCalculoDT            float64 `json:"base_calculo_dt"`
	
	IsentoSwing              bool    `json:"isento_swing"`
	IsentoExterior           bool    `json:"isento_exterior"`
	
	ImpostoSwing             float64 `json:"imposto_swing"`
	ImpostoExterior          float64 `json:"imposto_exterior"`
	ImpostoDT                float64 `json:"imposto_dt"`
	DarfAPagar               float64 `json:"darf_a_pagar"`
}

// --- ESTRUTURAS CONTÁBEIS E FISCAIS ---
type LancamentoContabil struct {
	ID             int       `json:"id"`
	UsuarioID      int       `json:"usuario_id"`
	DataLancamento time.Time `json:"data_lancamento"`
	DataLiquidacao time.Time `json:"data_liquidacao"` // D+2
	ContaDebito    string    `json:"conta_debito"`
	ContaCredito   string    `json:"conta_credito"`
	Valor          float64   `json:"valor"`
	Historico      string    `json:"historico"`
}

type LoteFiscal struct {
	ID                int       `json:"id"`
	UsuarioID         int       `json:"usuario_id"`
	Ticker            string    `json:"ticker"`
	DataEntrada       time.Time `json:"data_entrada"`
	QuantidadeInicial int       `json:"quantidade_inicial"`
	QuantidadeAtual   int       `json:"quantidade_atual"`
	PrecoCompra       float64   `json:"preco_compra"`
	CustosB3          float64   `json:"custos_b3"`
}

type LedgerFiscalMensal struct {
	ID                       int     `json:"id"`
	UsuarioID                int     `json:"usuario_id"`
	AnoMes                   string  `json:"ano_mes"` // "YYYY-MM"
	VolumeVendasSwing        float64 `json:"volume_vendas_swing"`
	LucroRealizadoSwing      float64 `json:"lucro_realizado_swing"`
	LucroRealizadoDayTrade   float64 `json:"lucro_realizado_daytrade"`
	PrejuizoCompensarSwing   float64 `json:"prejuizo_compensar_swing"`
	PrejuizoCompensarDT      float64 `json:"prejuizo_compensar_daytrade"`
	IrrfDedoDuro             float64 `json:"irrf_dedo_duro_retido"`
	DarfAPagar               float64 `json:"darf_a_pagar"`
	StatusPago               bool    `json:"status_pago"`
}

// DecisaoFiscal carrega o veredito se a ordem passa ou toma "HOLD"
type DecisaoFiscal struct {
	Aprovada         bool
	MotivoVeto       string
	ImpostoProjetado float64
}

// Estrutura para receber os dados digitados na tela de Login do celular
type LoginRequest struct {
	Login string `json:"login"`
	Senha string `json:"senha"`
}

// Estrutura para devolver a permissão de acesso ao celular
type LoginResponse struct {
	Sucesso   bool   `json:"sucesso"`
	Token     string `json:"token"`
	UsuarioID int    `json:"usuario_id"`
	Nome      string `json:"nome"`
	Role      string `json:"role"`
	Erro      string `json:"erro,omitempty"`
}

// Estrutura espelhada para o JWT (Garante que não teremos Import Cycle)
type JWTClaims struct {
	UsuarioID int    `json:"usuario_id"`
	Role      string `json:"role"`
	jwt.StandardClaims
}

// Estrutura do pedido que chega do React
type OtimizarRequestFrontend struct {
	UsuarioID int `json:"usuario_id"`
}

// Estrutura do payload que o Go vai enviar para o Python
type PayloadParaPython struct {
	UsuarioID int      `json:"usuario_id"`
	Tickers   []string `json:"tickers"`
}

// Estruturas de Resposta
type ResumoDashboard struct {
	CaixaLivre     float64 `json:"caixa_livre"`
	CustoAquisicao float64 `json:"custo_aquisicao"`
	Patrimonio     float64 `json:"patrimonio_total"`
}

type PontoHistorico struct {
	Data       string  `json:"data"`
	Patrimonio float64 `json:"patrimonio"`
}

// Estrutura para enviar os dados para o Python
type FallbackPayload struct {
	Ticker      string    `json:"ticker"`
	Fechamentos []float64 `json:"fechamentos"`
}

// 1. Contrato do Resumo Institucional (Custo Net, Drawdown, Sharpe)
type ResumoEstrategia struct {
	CapitalInicial  float64 `json:"capital_inicial"`
	CapitalAtualNet float64 `json:"capital_atual_net"` // Já com slippage e corretagem descontados
	LucroLiquido    float64 `json:"lucro_liquido_net"`
	MaxDrawdown     float64 `json:"max_drawdown_pct"`  // Ex: -8.5%
	SharpeRatio     float64 `json:"sharpe_ratio"`
	TotalOperacoes  int     `json:"total_operacoes"`
	WinRateNet      float64 `json:"win_rate_net_pct"`  // Taxa de acerto real
	CotacaoDolar    float64 `json:"cotacao_dolar"`
}

// 2. Contrato da Curva de Capital (Estabilidade de Regime)
type PontoCurvaCapital struct {
	Timestamp      time.Time `json:"timestamp"`
	PatrimonioBruto float64  `json:"patrimonio_bruto"`
	PatrimonioNet   float64  `json:"patrimonio_net"` // A linha que o Gustavo quer ver
	Volatilidade   float64   `json:"volatilidade_mercado"` // Gráfico de barras de risco
}

// 3. Contrato de Replay de Decisão (A Caixa-Preta Aberta)
type ReplayDecisao struct {
	ID             int       `json:"id"`
	Timestamp      time.Time `json:"timestamp"`
	Ativo          string    `json:"ativo"`
	ZScore         float64   `json:"z_score"`
	DensidadeProb  float64   `json:"densidade_prob"`
	ProbabilidadeIA float64  `json:"prob_ia_python"`
	FatorKelly     float64   `json:"fator_kelly_alocado"`
	AcaoExecutada  string    `json:"acao_executada"` // "COMPRA", "VENDA", "NEUTRO"
	CustoEstimado  float64   `json:"custo_friccao_bps"` // Atrito calculado
}