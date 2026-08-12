package quantadvisor.com.br.data.repository

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import quantadvisor.com.br.data.api.ExternalNewsApi
import quantadvisor.com.br.data.api.QuantApiService
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.ui.screens.terminal.LogMercado
import javax.inject.Inject
import javax.inject.Singleton

sealed class MarketEvent {
    data class Data(val log: LogMercado) : MarketEvent()
    data class Status(val isConnected: Boolean) : MarketEvent()
}

@Singleton
class MarketRepository @Inject constructor(
    private val api: QuantApiService,
    private val newsApi: ExternalNewsApi,
    private val client: OkHttpClient,
    private val gson: Gson = Gson()
) {
    // --- STREAMING (SSE) COM RECONEXÃƒO ---
    fun listenMarketFlow(): Flow<MarketEvent> = callbackFlow {
        Log.d("QuantAdvisor", "SSE: Iniciando conexÃ£o streaming...")
        
        val baseUrl = NetworkModule.BASE_URL.removeSuffix("/")
        val sseUrl = "$baseUrl/stream/mercado"
        val request = Request.Builder()
            .url(sseUrl)
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d("QuantAdvisor", "SSE: Streaming Conectado!")
                trySend(MarketEvent.Status(true))
            }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val log = gson.fromJson(data.trim(), LogMercado::class.java)
                    trySend(MarketEvent.Data(log))
                } catch (e: Exception) {
                    Log.e("QuantAdvisor", "SSE: Erro ao parsear JSON", e)
                }
            }
            override fun onClosed(eventSource: EventSource) {
                Log.w("QuantAdvisor", "SSE: Streaming Fechado")
                trySend(MarketEvent.Status(false))
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e("QuantAdvisor", "SSE: Falha na conexÃ£o streaming", t)
                trySend(MarketEvent.Status(false))
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { 
            Log.d("QuantAdvisor", "SSE: Cancelando streaming")
            eventSource.cancel() 
        }
    }

    // --- REST CALLS ---
    suspend fun getBuyingPower(): NetworkResult<BuyingPowerResponse> {
        return try {
            val response = api.getBuyingPower()
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao buscar poder de compra")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getSinaisHFT(): NetworkResult<List<SignalHFTResponse>> {
        return try {
            val response = api.getSinaisHFT()
            if (response.isSuccessful) NetworkResult.Success(response.body() ?: emptyList())
            else NetworkResult.Error("Erro ao buscar sinais HFT")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getExecucoesTWAP(): NetworkResult<List<TWAPExecutionResponse>> {
        return try {
            val response = api.getExecucoesTWAP()
            if (response.isSuccessful) NetworkResult.Success(response.body() ?: emptyList())
            else NetworkResult.Error("Erro ao buscar histórico TWAP")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun criarUsuario(request: NovaContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.criarUsuario(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao criar usuário")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun realizarCambio(request: CambioRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.realizarCambio(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no câmbio")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun otimizarCarteira(usuarioId: Int): NetworkResult<GenericResponse> {
        return try {
            val response = api.otimizarCarteira(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na otimização")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun preverLSTM(ticker: String): NetworkResult<LstmResponse> {
        return try {
            val response = api.preverLSTM(ticker)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na previsão LSTM")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun agenteCausalidade(body: Map<String, String>): NetworkResult<Map<String, Any>> {
        return try {
            val response = api.agenteCausalidade(body)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na inferência causal")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarUsuarios(): NetworkResult<List<UsuarioResumo>> {
        return try {
            val response = api.listarUsuarios()
            if (response.isSuccessful) NetworkResult.Success(response.body() ?: emptyList())
            else NetworkResult.Error("Erro ${response.code()}")
        } catch (e: Exception) { NetworkResult.Error(e.message ?: "Falha na rede", e) }
    }

    suspend fun listarPerfis(): NetworkResult<List<PerfilInvestidor>> {
        return try {
            val response = api.listarPerfis()
            if (response.isSuccessful) NetworkResult.Success(response.body() ?: emptyList())
            else NetworkResult.Error("Erro perfis")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getResumoDashboard(usuarioId: Int): NetworkResult<ResumoDashboard> {
        return try {
            val response = api.getResumoDashboard(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro Dashboard")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getDashboardMacro(): NetworkResult<MacroResponse> {
        return try {
            val response = api.getDashboardMacro()
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro Macro")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getDashboardHistorico(usuarioId: Int): NetworkResult<List<PontoHistorico>> {
        return try {
            val response = api.getDashboardHistorico(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro histÃ³rico")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun obterInfoUsuario(id: Int? = null): NetworkResult<UsuarioResumo> {
        return try {
            val response = api.obterInfoUsuario(id)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("UsuÃ¡rio nÃ£o encontrado")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getResumoFiscal(usuarioId: Int, anoMes: String): NetworkResult<ResumoFiscalMensal> {
        return try {
            val response = api.getResumoFiscal(usuarioId, anoMes)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro fiscal")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarLancamentos(usuarioId: Int): NetworkResult<List<ItemLancamento>> {
        return try {
            val response = api.listarLancamentos(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro lanÃ§amentos")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarLotes(usuarioId: Int): NetworkResult<List<LoteFiscal>> {
        return try {
            val response = api.listarLotes(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro lotes")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getInstitucionalResumo(usuarioId: Int? = null): NetworkResult<ResumoEstrategia> {
        return try {
            val response = api.getInstitucionalResumo(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro institucional")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getCurvaCapital(usuarioId: Int? = null): NetworkResult<List<PontoCurvaCapital>> {
        return try {
            val response = api.getCurvaCapital(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro curva")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getReplayDecisao(usuarioId: Int? = null): NetworkResult<List<ReplayDecisao>> {
        return try {
            val response = api.getReplayDecisao(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro replay")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun runBacktest(ticker: String): NetworkResult<BacktestResponse> {
        return try {
            val response = api.backtest(ticker)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro backtest")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun runMonteCarlo(ticker: String): NetworkResult<MonteCarloResponse> {
        return try {
            val response = api.monteCarlo(ticker)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro monte carlo")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getNoticias(): NetworkResult<List<Noticia>> {
        return try {
            val rssUrl = "https://news.google.com/rss/search?q=mercado+financeiro+bolsa+de+valores+economia&hl=pt-BR&gl=BR&ceid=BR:pt-419"
            val fullUrl = "https://api.rss2json.com/v1/api.json?rss_url=$rssUrl"
            val response = newsApi.getGoogleNews(fullUrl)
            if (response.isSuccessful && response.body() != null) {
                val mapped = response.body()!!.items.map { item ->
                    Noticia(item.link, item.title, item.description, "Google News", item.pubDate, "MÃ‰DIO", item.link)
                }
                NetworkResult.Success(mapped)
            } else NetworkResult.Error("Falha nas notÃ­cias")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getDetalhesAtivo(ticker: String): NetworkResult<AssetAnalysis> {
        return try {
            val response = api.getDetalhesAtivo(ticker)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val ia = body.ia_status ?: emptyMap()
                val fund = (body.fundamentos?.get("quoteSummary") as? Map<*, *>)
                    ?.let { (it["result"] as? List<*>)?.get(0) as? Map<*, *> }
                    ?.get("financialData") as? Map<*, *>

                val analysis = AssetAnalysis(
                    ticker = ticker,
                    preco_atual = (ia["preco_atual"] as? Double) ?: 0.0,
                    z_score = (ia["z_score"] as? Double) ?: 0.0,
                    volatilidade = (ia["risco_var"] as? Double) ?: 0.0,
                    ai_decision = (ia["sinal"] as? String) ?: "NEUTRO",
                    ai_score = (ia["ai_score"] as? Double)?.toInt() ?: 50,
                    roe = (fund?.get("returnOnEquity") as? Map<*, *>)?.get("raw") as? Double ?: 0.0,
                    dividend_yield = (fund?.get("dividendYield") as? Map<*, *>)?.get("raw") as? Double ?: 0.0,
                    p_e = (fund?.get("trailingPE") as? Map<*, *>)?.get("raw") as? Double ?: 0.0,
                    margem_liquida = (fund?.get("profitMargins") as? Map<*, *>)?.get("raw") as? Double ?: 0.0
                )
                NetworkResult.Success(analysis)
            } else NetworkResult.Error("Ativo nÃ£o encontrado")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getCarteira(usuarioId: Int? = null): NetworkResult<CarteiraResponse> {
        return try {
            val response = api.getCarteira(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro carteira")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun enviarOrdem(request: OrdemRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.enviarOrdem(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ordem")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun togglePilotoAutomatico(request: TogglePilotoReq): NetworkResult<GenericResponse> {
        return try {
            val response = api.togglePilotoAutomatico(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro piloto")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getAuditoriaMercado(): NetworkResult<AuditoriaResponse> {
        return try {
            val response = api.getAuditoriaMercado()
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro auditoria")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun limparCarrinho(ids: List<Int>): NetworkResult<GenericResponse> {
        return try {
            val response = api.limparCarrinho(LimparCarrinhoReq(ids))
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro limpar")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarCarrinho(usuarioId: Int? = null): NetworkResult<List<CarrinhoItem>> {
        return try {
            val response = api.listarCarrinho(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro carrinho")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getParametros(usuarioId: Int? = null): NetworkResult<ParametrosOperacionais> {
        return try {
            val response = api.getParametros(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro parÃ¢metros")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun configurarRobo(request: ParametrosOperacionais): NetworkResult<GenericResponse> {
        return try {
            val response = api.configurarRobo(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro configurar")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun editarUsuario(request: EditarContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.editarUsuario(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro editar")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun deletarUsuario(request: DeletarContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.deletarUsuario(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro deletar")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun login(request: LoginRequest): NetworkResult<LoginResponse> {
        return try {
            val response = api.login(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro login")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun solicitarCadastro(request: NovaContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.solicitarCadastro(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro solicitar")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun validarCadastro(request: ValidarCadastroRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.validarCadastro(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro validar")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }
}