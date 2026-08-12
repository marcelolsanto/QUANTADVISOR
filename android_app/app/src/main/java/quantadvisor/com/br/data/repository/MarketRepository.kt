package quantadvisor.com.br.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.api.ExternalNewsApi
import quantadvisor.com.br.data.api.QuantApiService
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.di.NetworkModule
import java.util.concurrent.TimeUnit
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
    @param:ApplicationContext private val context: Context,
    private val gson: Gson = Gson()
) {
    // --- STREAMING (SSE) ---
    // 🛡️ REFINADO PARA HFT: Derivado dinamicamente de NetworkModule.BASE_URL
    fun listenMarketFlow(ctx: Context): Flow<MarketEvent> = callbackFlow {
        Log.d("QuantAdvisor", "SSE: Iniciando conexão streaming...")
        
        val sseClient = client.newBuilder()
            .apply { interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor } }
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val token = SecurityManager.getToken(ctx)
        
        // 🛡️ URL DINÂMICA (Se adapta a Produção, Emulador ou Celular Físico via IP)
        val url = quantadvisor.com.br.di.NetworkModule.BASE_URL + "stream/mercado"
        
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            // 🛡️ REMOVIDO: O interceptor do OkHttpClient injetado (via NetworkModule) 
            // já adiciona automaticamente o cabeçalho "Authorization: Bearer <token>".
            // Adicionar aqui causava cabeçalho duplicado e erro 400/401 no backend Go.
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d("QuantAdvisor", "SSE: Streaming Conectado!")
                trySend(MarketEvent.Status(true))
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val rawJson = data.trim()
                    if (rawJson.isEmpty()) return

                    val rootObject = JsonParser.parseString(rawJson).asJsonObject
                    
                    // A API Go embrulha o payload do Python em {"tipo": "TICK_MERCADO", "dados": {...}}
                    val jsonObject = if (rootObject.has("dados")) rootObject.getAsJsonObject("dados") else rootObject

                    val ativo = jsonObject.get("ativo")?.asString ?: "---"
                    val preco = jsonObject.get("preco_atual")?.asDouble ?: 0.0
                    val zScore = jsonObject.get("z_score")?.asDouble ?: 0.0
                    val riscoVar = jsonObject.get("risco_var")?.asDouble ?: 0.0
                    val distVwap = jsonObject.get("distancia_vwap_perc")?.asDouble ?: 0.0
                    val volZ = jsonObject.get("volume_zscore")?.asDouble ?: 0.0
                    
                    val sinaisPerfilMap = mutableMapOf<String, String>()
                    if (jsonObject.has("sinais_perfil")) {
                        val perfis = jsonObject.getAsJsonObject("sinais_perfil")
                        perfis.entrySet().forEach { (k, v) -> sinaisPerfilMap[k] = v.asString }
                    }
                    
                    val sinalGeral = jsonObject.get("sinal")?.asString ?: "NEUTRO"

                    val log = LogMercado(
                        ativo = ativo,
                        sinal = sinalGeral,
                        precoAtual = preco,
                        zScore = zScore,
                        riscoVar = riscoVar,
                        distVwap = distVwap,
                        volZScore = volZ,
                        sinaisPerfil = sinaisPerfilMap,
                        hora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    )
                    
                    trySend(MarketEvent.Data(log))
                } catch (e: Exception) {
                    Log.e("QuantAdvisor", "SSE: Erro ao parsear tick: ${e.message}")
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d("QuantAdvisor", "SSE: Conexão fechada.")
                trySend(MarketEvent.Status(false))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e("QuantAdvisor", "SSE: Falha na conexão!", t)
                trySend(MarketEvent.Status(false))
            }
        }

        val eventSource = EventSources.createFactory(sseClient).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    // --- MÉTODOS REST (V7 UPDATES) ---
    
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

    suspend fun iniciarIngestaoManual(): NetworkResult<GenericResponse> {
        return try {
            val response = api.iniciarIngestaoManual()
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao iniciar ingestão HFT")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun criarUsuario(request: NovaContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.criarUsuario(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao criar usuário")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    // --- MÉTODOS EXISTENTES ---

    suspend fun login(request: LoginRequest): NetworkResult<LoginResponse> {
        return try {
            val response = api.login(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no login")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun solicitarCadastro(request: NovaContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.solicitarCadastro(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na solicitação")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun validarCadastro(request: ValidarCadastroRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.validarCadastro(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na validação")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarUsuarios(): NetworkResult<List<UsuarioResumo>> {
        return try {
            val response = api.listarUsuarios()
            if (response.isSuccessful) NetworkResult.Success(response.body() ?: emptyList())
            else NetworkResult.Error("Erro ao listar usuários")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun obterInfoUsuario(id: Int? = null): NetworkResult<UsuarioResumo> {
        return try {
            val response = api.obterInfoUsuario(id)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao obter info do usuário")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun editarUsuario(request: EditarContaRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.editarUsuario(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao editar usuário")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun deletarUsuario(id: Int): NetworkResult<GenericResponse> {
        return try {
            val response = api.deletarUsuario(DeletarContaRequest(id))
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao deletar usuário")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarPerfis(): NetworkResult<List<PerfilInvestidor>> {
        return try {
            val response = api.listarPerfis()
            if (response.isSuccessful) NetworkResult.Success(response.body() ?: emptyList())
            else NetworkResult.Error("Erro ao listar perfis")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getCarteira(usuarioId: Int? = null, moeda: String? = null): NetworkResult<CarteiraResponse> {
        return try {
            val response = api.getCarteira(usuarioId, moeda)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao obter carteira")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getResumoDashboard(usuarioId: Int): NetworkResult<ResumoDashboard> {
        return try {
            val response = api.getResumoDashboard(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no resumo do dashboard")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getDashboardMacro(): NetworkResult<MacroResponse> {
        return try {
            val response = api.getDashboardMacro()
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no dashboard macro")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getDashboardHistorico(usuarioId: Int): NetworkResult<List<PontoHistorico>> {
        return try {
            val response = api.getDashboardHistorico(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no histórico do dashboard")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun enviarOrdem(request: OrdemRequest): NetworkResult<GenericResponse> {
        return try {
            val response = api.enviarOrdem(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao enviar ordem")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun togglePilotoAutomatico(request: TogglePilotoReq): NetworkResult<GenericResponse> {
        return try {
            val response = api.togglePilotoAutomatico(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao alternar piloto automático")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getHistoricoOrdens(usuarioId: Int? = null): NetworkResult<List<OrdemExecutada>> {
        return try {
            val response = api.getHistoricoOrdens(usuarioId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!.ordens ?: emptyList())
            } else NetworkResult.Error("Erro ao carregar histórico")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarCarrinho(usuarioId: Int? = null): NetworkResult<List<CarrinhoItem>> {
        return try {
            val response = api.listarCarrinho(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao listar carrinho")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun limparCarrinho(ids: List<Int>): NetworkResult<GenericResponse> {
        return try {
            val response = api.limparCarrinho(LimparCarrinhoReq(ids))
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao limpar carrinho")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getParametros(usuarioId: Int? = null): NetworkResult<ParametrosOperacionais> {
        return try {
            val response = api.getParametros(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao obter parâmetros")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun configurarRobo(request: ParametrosOperacionais): NetworkResult<GenericResponse> {
        return try {
            val response = api.configurarRobo(request)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao configurar robô")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarLancamentos(usuarioId: Int): NetworkResult<List<ItemLancamento>> {
        return try {
            val response = api.listarLancamentos(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao listar lançamentos")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun listarLotes(usuarioId: Int): NetworkResult<List<LoteFiscal>> {
        return try {
            val response = api.listarLotes(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao listar lotes")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getResumoFiscal(usuarioId: Int, anoMes: String): NetworkResult<ResumoFiscalMensal> {
        return try {
            val response = api.getResumoFiscal(usuarioId, anoMes)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no resumo fiscal")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getDetalhesAtivo(ticker: String): NetworkResult<AssetAnalysisResponse> {
        return try {
            val response = api.getDetalhesAtivo(ticker)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao obter detalhes do ativo")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun runBacktest(ticker: String): NetworkResult<BacktestResponse> {
        return try {
            val response = api.backtest(ticker)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao rodar backtest")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun runMonteCarlo(ticker: String): NetworkResult<MonteCarloResponse> {
        return try {
            val response = api.monteCarlo(ticker)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro ao rodar Monte Carlo")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getInstitucionalResumo(usuarioId: Int? = null): NetworkResult<ResumoEstrategia> {
        return try {
            val response = api.getInstitucionalResumo(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no resumo institucional")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getCurvaCapital(usuarioId: Int? = null): NetworkResult<List<PontoCurvaCapital>> {
        return try {
            val response = api.getCurvaCapital(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na curva de capital")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getReplayDecisao(usuarioId: Int? = null): NetworkResult<List<ReplayDecisao>> {
        return try {
            val response = api.getReplayDecisao(usuarioId)
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro no replay de decisão")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getAuditoriaMercado(): NetworkResult<AuditoriaResponse> {
        return try {
            val response = api.getAuditoriaMercado()
            if (response.isSuccessful && response.body() != null) NetworkResult.Success(response.body()!!)
            else NetworkResult.Error("Erro na auditoria")
        } catch (e: Exception) { NetworkResult.Error("Falha na rede", e) }
    }

    suspend fun getNoticias(): NetworkResult<List<Noticia>> {
        return try {
            val listaNoticias = mutableListOf<Noticia>()
            
            // 1. GOOGLE NEWS - Macro Brasil (B3, Selic, Dólar)
            val googleQuery = "B3 OR IBOVESPA OR Selic OR Dólar OR inflação when:1d"
            val googleMacroRss = "https://news.google.com/rss/search?q=${java.net.URLEncoder.encode(googleQuery, "UTF-8")}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
            val googleMacroUrl = "https://api.rss2json.com/v1/api.json?rss_url=${java.net.URLEncoder.encode(googleMacroRss, "UTF-8")}"
            
            // 2. GOOGLE FINANCE (Top Stories do Google News Finance)
            val googleFinanceRss = "https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TVdZU00xVnpSREp0ZUVnc0p3b0FQAQ?hl=pt-BR&gl=BR&ceid=BR:pt-419"
            val googleFinanceUrl = "https://api.rss2json.com/v1/api.json?rss_url=${java.net.URLEncoder.encode(googleFinanceRss, "UTF-8")}"

            // 3. YAHOO FINANCE - Brasil (Artigos exclusivos Yahoo Finanças BR)
            val yahooBrRss = "https://news.google.com/rss/search?q=site:br.financas.yahoo.com+when:1d&hl=pt-BR&gl=BR&ceid=BR:pt-419"
            
            // 4. YAHOO FINANCE - Global (NYSE, NASDAQ, Fed)
            val yahooGlobalRss = "https://finance.yahoo.com/news/rss"
            
            coroutineScope {
                val googleMacroJob = async { newsApi.getGoogleNews(googleMacroUrl) }
                val googleFinanceJob = async { newsApi.getGoogleNews(googleFinanceUrl) }
                val yahooBrJob = async { newsApi.getGoogleNews("https://api.rss2json.com/v1/api.json?rss_url=${java.net.URLEncoder.encode(yahooBrRss, "UTF-8")}") }
                val yahooGlobalJob = async { newsApi.getGoogleNews("https://api.rss2json.com/v1/api.json?rss_url=${java.net.URLEncoder.encode(yahooGlobalRss, "UTF-8")}") }

                val respGoogleMacro = googleMacroJob.await()
                if (respGoogleMacro.isSuccessful && respGoogleMacro.body()?.status == "ok") {
                    respGoogleMacro.body()!!.items.forEach { item ->
                        listaNoticias.add(Noticia(
                            id = if (item.guid.isEmpty()) item.link else item.guid,
                            titulo = item.title.substringBefore(" - "),
                            resumo = item.description.replace(Regex("<[^>]*>"), "").trim(),
                            fonte = "Google Finance (BR)",
                            hora = item.pubDate,
                            impacto = "MÉDIO",
                            link = item.link
                        ))
                    }
                }

                val respGoogleFinance = googleFinanceJob.await()
                if (respGoogleFinance.isSuccessful && respGoogleFinance.body()?.status == "ok") {
                    respGoogleFinance.body()!!.items.forEach { item ->
                        listaNoticias.add(Noticia(
                            id = if (item.guid.isEmpty()) item.link else item.guid,
                            titulo = item.title.substringBefore(" - "),
                            resumo = item.description.replace(Regex("<[^>]*>"), "").trim(),
                            fonte = "Google Finance",
                            hora = item.pubDate,
                            impacto = "ALTO",
                            link = item.link
                        ))
                    }
                }

                val respYahooBr = yahooBrJob.await()
                if (respYahooBr.isSuccessful && respYahooBr.body()?.status == "ok") {
                    respYahooBr.body()!!.items.forEach { item ->
                        listaNoticias.add(Noticia(
                            id = if (item.guid.isEmpty()) item.link else item.guid,
                            titulo = item.title.substringBefore(" - "),
                            resumo = item.description.replace(Regex("<[^>]*>"), "").trim(),
                            fonte = "Yahoo Finance (BR)",
                            hora = item.pubDate,
                            impacto = "ALTO",
                            link = item.link
                        ))
                    }
                }

                val respYahooGlobal = yahooGlobalJob.await()
                if (respYahooGlobal.isSuccessful && respYahooGlobal.body()?.status == "ok") {
                    respYahooGlobal.body()!!.items.forEach { item ->
                        listaNoticias.add(Noticia(
                            id = if (item.guid.isEmpty()) item.link else item.guid,
                            titulo = item.title,
                            resumo = item.description.replace(Regex("<[^>]*>"), "").trim(),
                            fonte = "Yahoo Finance Global",
                            hora = item.pubDate,
                            impacto = "ALTO",
                            link = item.link
                        ))
                    }
                }
            }

            if (listaNoticias.isNotEmpty()) {
                val finalNews = listaNoticias
                    .distinctBy { it.titulo.lowercase().trim() }
                    .sortedByDescending { it.hora }
                
                Log.d("QuantAdvisor", "Notícias Unificadas (Google/Yahoo): ${finalNews.size}")
                NetworkResult.Success(finalNews)
            } else {
                NetworkResult.Error("Sem notícias disponíveis no momento.")
            }
        } catch (e: Exception) { 
            NetworkResult.Error("Falha na rede: ${e.message}", e) 
        }
    }
}
