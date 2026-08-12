package quantadvisor.com.br.data.model

import com.google.gson.annotations.SerializedName

// ==========================================
// AUTENTICAÃ‡ÃƒO E USUÃRIOS
// ==========================================

data class LoginRequest(
    val login: String,
    val senha: String
)

data class LoginResponse(
    val sucesso: Boolean,
    val token: String?,
    val usuario_id: Int?,
    val nome: String?,
    val role: String?,
    val erro: String?
)

data class NovaContaRequest(
    val nome_cliente: String,
    val email: String,
    val whatsapp: String,
    val login: String,
    val senha: String,
    val perfil_risco: String,
    val saldo_inicial: Double,
    val saldo_usd: Double = 0.0,
    val role: String = "CLIENTE",
    val piloto_automatico: Boolean = false
)

data class ValidarCadastroRequest(
    val email: String,
    val codigo: String
)

data class GenericResponse(
    val sucesso: Boolean,
    val mensagem: String?,
    val erro: String?,
    val codigo_teste: String?
)

data class UsuarioResumo(
    val id: Int,
    val login: String? = "",
    val nome: String? = "",
    val email: String? = "",
    val whatsapp: String? = "",
    @SerializedName("celular") val celular: String? = "",
    val perfil_risco: String? = "Moderado",
    val saldo_brl: Double? = 0.0,
    val saldo_usd: Double? = 0.0,
    @SerializedName("saldo_disponivel") val saldoDisponivelRaw: Double? = null,
    val lucro_acumulado: Double? = 0.0,
    val piloto_automatico: Boolean? = false,
    val role: String? = "CLIENTE",
    val ultimaOperacao: String? = "---",
    val volumeNegociado: Double? = 0.0
) {
    val saldo_disponivel: Double
        get() = saldo_brl ?: saldoDisponivelRaw ?: 0.0
}

data class TogglePilotoReq(
    val usuario_id: Int,
    val estado: Boolean
)

data class DeletarContaRequest(
    val id: Int
)

data class PerfilInvestidor(
    val id: Int,
    @SerializedName("nome_usuario") val nomeUsuario: String,
    @SerializedName("perfil_comportamental") val perfilComportamental: String
)

data class EditarContaRequest(
    val id: Int,
    val nome_cliente: String,
    val email: String,
    val whatsapp: String,
    val login: String,
    val perfil_risco: String,
    val saldo_inicial: Double = 0.0,
    val saldo_usd: Double = 0.0,
    val senha: String? = null,
    val role: String? = null,
    val piloto_automatico: Boolean = false
)

// ==========================================
// DASHBOARD E PORTFÃ“LIO
// ==========================================

data class CarteiraResponse(
    val sucesso: Boolean,
    val posicoes: List<AtivoPatrimonio> = emptyList(),
    val saldo_brl: Double = 0.0,
    val saldo_usd: Double = 0.0,
    val nome_cliente: String = ""
)

data class AtivoPatrimonio(
    val ticker: String,
    val quantidade: Int,
    val preco_medio: Double,
    @SerializedName("preco_atual") val preco_atual: Double,
    @SerializedName("lucro_prejuizo_financeiro") val lucro_prejuizo: Double,
    @SerializedName("lucro_prejuizo_percentual") val lucro_percentual: Double = 0.0,
    val moeda: String = "BRL"
)

data class ResumoDashboard(
    val caixa_livre: Double,
    val custo_aquisicao: Double,
    val patrimonio_total: Double
)

data class MacroResponse(
    val aum_total: Double,
    val caixa_global: Double,
    val custodia_global: Double,
    val total_clientes: Int,
    val cotacao_dolar_ativa: Double,
    val regime_atual: String,
    val clientes: List<UsuarioResumo> = emptyList()
)

data class PontoHistorico(
    val data: String,
    val patrimonio: Double
)

// ==========================================
// OPERAÃ‡Ã•ES E TERMINAL
// ==========================================

data class OrdemRequest(
    val usuario_id: Int,
    val ticker: String,
    val quantidade: Int,
    val preco: Double,
    val tipo_ordem: String,
    val moeda: String = "BRL",
    val taxa_cambio_momento: Double = 1.0,
    val volume_brl: Double = 0.0
)

data class OrdemExecutada(
    val id: Int,
    val ticker: String,
    @SerializedName("tipo_ordem") val tipoOrdem: String,
    val quantidade: Int,
    @SerializedName("preco_execucao") val precoExecucao: Double,
    @SerializedName("data_hora") val dataHora: String
)

data class AuditoriaResponse(
    val sucesso: Boolean,
    val regime: String,
    val total: Int,
    val recomendacoes: List<Map<String, Any>> = emptyList(),
    val ativos_monitorados: Int = 0,
    val sinais_ativos: Int = 0,
    val ultima_varredura: String = ""
)

data class CambioRequest(
    val usuario_id: Int,
    val direcao: String,
    val valor_origem: Double
)

data class CarrinhoItem(
    val id: Int,
    val ticker: String,
    val tipo: String,
    val quantidade: Int,
    val preco: Double
)

data class LimparCarrinhoReq(
    val ids: List<Int>
)

// ==========================================
// COMPLIANCE E FISCAL
// ==========================================

data class ItemLancamento(
    val id: Int,
    @SerializedName("conta_debito") val debito: String,
    @SerializedName("conta_credito") val credito: String,
    val valor: Double,
    val historico: String,
    @SerializedName("data_lancamento") val data: String,
    @SerializedName("data_liquidacao") val liquidacao: String
)

data class LoteFiscal(
    val id: Int,
    @SerializedName("usuario_id") val usuarioId: Int,
    val ticker: String,
    @SerializedName("data_entrada") val dataEntrada: String,
    @SerializedName("quantidade_inicial") val quantidadeInicial: Int,
    @SerializedName("quantidade_atual") val quantidadeAtual: Int,
    @SerializedName("preco_compra") val precoCompra: Double,
    @SerializedName("custos_b3") val custosB3: Double
)

data class ResumoFiscalMensal(
    val ano_mes: String,
    val volume_vendas_swing: Double,
    val lucro_realizado_swing: Double,
    val volume_vendas_exterior: Double = 0.0,
    val lucro_realizado_exterior: Double = 0.0,
    val lucro_realizado_daytrade: Double,
    val irrf_dedo_duro_retido: Double,
    val prejuizo_anterior_swing: Double,
    val prejuizo_anterior_exterior: Double = 0.0,
    val prejuizo_anterior_dt: Double,
    val base_calculo_swing: Double,
    val base_calculo_exterior: Double = 0.0,
    val base_calculo_dt: Double,
    val isento_swing: Boolean,
    val isento_exterior: Boolean = false,
    val imposto_swing: Double,
    val imposto_exterior: Double = 0.0,
    val imposto_dt: Double,
    val darf_a_pagar: Double
)

// ==========================================
// CONFIGURAÃ‡Ã•ES
// ==========================================

data class ParametrosOperacionais(
    val usuario_id: Int,
    val multiplicador_kelly: Double,
    val limite_concentracao_ativo: Double,
    val gatilho_rebalanceamento: Double,
    val multiplicador_stop_var: Double,
    val piso_max_drawdown: Double,
    val z_score_compra_forte: Double,
    val z_score_venda_lucro: Double,
    val z_score_stop_loss: Double,
    val bloqueio_sentimento_negativo: Boolean,
    val custo_friccao_padrao: Double,
    val modo_isencao_fiscal_estrita: Boolean
)

// ==========================================
// ANÃLISE E IA (RESPOSTAS FORTES)
// ==========================================

// ==========================================
// HFT & WALLET MODELS
// ==========================================

data class BuyingPowerResponse(
    val buying_power: Double,
    val currency: String,
    val timestamp: String
)

data class SignalHFTResponse(
    val strategy: String,
    val action: String,
    val asset_a: String,
    val asset_b: String,
    val target_qty: Double,
    val price: Double? = null,
    val timestamp: String
)

data class TWAPExecutionResponse(
    val order_id: String,
    val symbol: String,
    val side: String,
    val qty: Double,
    val price: Double,
    val slice_index: Int,
    val total_slices: Int,
    val status: String,
    val timestamp: String
)

// ==========================================
// RESULTADOS DE REDE (SEALED CLASS)
// ==========================================

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.exception
}

data class AssetAnalysisResponse(
    val sucesso: Boolean,
    val historico: List<Double> = emptyList(),
    val fundamentos: Map<String, Any>? = null,
    val ia_status: Map<String, Any>? = null
)

data class BacktestResponse(
    val sucesso: Boolean,
    val erro: String? = null,
    val win_rate: Double = 0.0,
    val max_drawdown: Double = 0.0,
    val total_trades: Int = 0,
    val kelly_recomendado_perc: Double = 0.0,
    val trades: List<TradeBacktest> = emptyList()
)

data class MonteCarloResponse(
    val sucesso: Boolean,
    val erro: String? = null,
    val modelo_utilizado: String = "",
    val projecao_1_ano: Map<String, Double> = emptyMap(),
    val risco_estrutural: Map<String, Any> = emptyMap(),
    val grafico: List<Map<String, Any>> = emptyList()
)

data class LstmResponse(
    val sucesso: Boolean,
    val erro: String? = null,
    val preco_atual: Double = 0.0,
    val previsao_t1: Double = 0.0,
    val sinal_rede_neural: String = "",
    val variacao_projetada_perc: Double = 0.0,
    val detalhes_modelo: String = ""
)

data class RiscoResponse(
    val sucesso: Boolean,
    val ativos_analisados: Int = 0,
    val alertas_concentracao: List<Map<String, Any>> = emptyList(),
    val oportunidades_hedge: List<Map<String, Any>> = emptyList(),
    val heatmap_base64: String = ""
)

data class ProjecaoResponse(
    val sucesso: Boolean,
    val composicao_atual: Map<String, Any> = emptyMap(),
    val taxas_aplicadas: Map<String, Any> = emptyMap(),
    val projecao_anual: List<Map<String, Any>> = emptyList(),
    val projecao_mensal: List<Map<String, Any>> = emptyList()
)

data class AssetAnalysis(
    val ticker: String,
    val preco_atual: Double,
    val z_score: Double,
    val volatilidade: Double,
    val ai_decision: String,
    val p_e: Double = 0.0,
    val p_vp: Double = 0.0,
    val dividend_yield: Double = 0.0,
    val roe: Double = 0.0,
    val margem_liquida: Double = 0.0,
    val divida_ebitda: Double = 0.0,
    val rsi: Double = 0.0,
    val beta: Double = 0.0,
    val ai_score: Int = 50,
    val variacao_dia: Double = 0.0
)

data class ResumoEstrategia(
    val capital_inicial: Double,
    val capital_atual_net: Double,
    val lucro_liquido_net: Double,
    @SerializedName("max_drawdown") val max_drawdown: Double,
    val total_operacoes: Int,
    @SerializedName("win_rate_net") val win_rate_net: Double,
    val cotacao_dolar: Double = 0.0,
    val sharpe_ratio: Double = 0.0
)

data class PontoCurvaCapital(
    val timestamp: String,
    val patrimonio_net: Double,
    val volatilidade_mercado: Double
)

data class ReplayDecisao(
    val timestamp: String,
    val ativo: String,
    val z_score: Double,
    val fator_kelly_alocado: Double,
    val acao_executada: String,
    @SerializedName("custo_friccao_bps") val custo_friccao_bps: Double,
    val perfil_risco: String,
    val regime_mercado: String
)

data class TradeBacktest(
    val date: String,
    val side: String,
    val price: Double,
    val result: Double
)

// ==========================================
// NOTÃCIAS (GOOGLE NEWS RSS2JSON)
// ==========================================

data class Noticia(
    val id: String,
    val titulo: String,
    val resumo: String,
    val fonte: String,
    val hora: String,
    val impacto: String,
    val link: String? = null
)

data class Rss2JsonRoot(
    val status: String,
    val items: List<RssItem>
)

data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String,
    val content: String,
    val description: String
)