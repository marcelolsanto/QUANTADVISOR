package quantadvisor.com.br.ui.screens.terminal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.data.repository.MarketEvent
import quantadvisor.com.br.data.repository.MarketRepository
import quantadvisor.com.br.session.MarketSession
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class TerminalUiState(
    val selectedUser: UsuarioResumo? = null,
    val dashboard: ResumoDashboard? = null,
    val carteira: CarteiraResponse? = null,
    val historico: List<OrdemExecutada> = emptyList(),
    val carrinho: List<CarrinhoItem> = emptyList(),
    val auditoria: List<LogMercado> = emptyList(),
    val isLoading: Boolean = true,
    val isConnected: Boolean = false,
    val logs: List<LogMercado> = emptyList(),
    val allUsers: List<UsuarioResumo> = emptyList(),
    val orderStatus: String? = null,
    val isSendingOrder: Boolean = false,
    val totalPl: Double = 0.0,
    val ultimoAlvo: LogMercado? = null,
    val avgZ: Double = 0.0,
    val avgVar: Double = 0.0,
    val buyCount: Int = 0,
    val sellCount: Int = 0,
    val neutralCount: Int = 0,
    val buyPct: Float = 0f,
    val sellPct: Float = 0f,
    val neutralPct: Float = 0f,
    val oportunidades: List<String> = emptyList(),
    val noticias: List<Noticia> = emptyList()
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository,
    val marketSession: MarketSession
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var initialized = false

    init {
        // 🛡️ STREAMING SSE ATIVADO (SUBSTITUIÇÃO POLLING AUDITORIA)
        observeMarketThrottled()
        
        viewModelScope.launch {
            marketSession.currentMarket.collectLatest { moeda ->
                if (initialized) {
                    val uid = _uiState.value.selectedUser?.id ?: SecurityManager.getUsuarioId(getApplication())
                    if (uid != 0) refreshFinancials(uid, moeda)
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeMarketThrottled() {
        // 🛡️ CONECTANDO AO NOVO TÚNEL SSE (LOCAL OU PROD)
        repository.listenMarketFlow(getApplication())
            .buffer(100)
            .onEach { event ->
                when (event) {
                    is MarketEvent.Data -> addLogOptimized(event.log)
                    is MarketEvent.Status -> _uiState.update { it.copy(isConnected = event.isConnected) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun initTerminal(userId: Int) {
        if (initialized && _uiState.value.selectedUser?.id == userId) return
        
        // 🚀 ACORDA O BACKEND PARA O HFT LIVE FLOW
        startHftIngestion()
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val uid = if (userId == -1) SecurityManager.getUsuarioId(getApplication()) else userId
            
            if (uid == 0) {
                _uiState.update { it.copy(isLoading = false, orderStatus = "Sessão inválida.") }
                return@launch
            }

            val usersResult = repository.listarUsuarios()
            if (usersResult is NetworkResult.Success<*>) {
                _uiState.update { it.copy(allUsers = (usersResult as NetworkResult.Success<List<UsuarioResumo>>).data) }
            }

            val userResult = repository.obterInfoUsuario(uid)
            if (userResult is NetworkResult.Success<*>) {
                val user = (userResult as NetworkResult.Success<UsuarioResumo>).data
                _uiState.update { it.copy(selectedUser = user) }
                refreshFinancials(uid, marketSession.currentMarket.value)
                initialized = true
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun switchUser(user: UsuarioResumo) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedUser = user, isLoading = true) }
            refreshFinancials(user.id, marketSession.currentMarket.value)
        }
    }

    fun startHftIngestion() {
        viewModelScope.launch {
            try {
                Log.d("QuantAdvisor", "Iniciando disparo da esteira HFT no backend...")
                val result = repository.iniciarIngestaoManual()
                if (result is NetworkResult.Success<*>) {
                    Log.d("QuantAdvisor", "Esteira HFT ativada com sucesso!")
                } else {
                    Log.e("QuantAdvisor", "Falha ao acordar esteira HFT: ${(result as? NetworkResult.Error)?.message}")
                }
            } catch (e: Exception) {
                Log.e("QuantAdvisor", "Exceção ao acordar esteira HFT", e)
            }
        }
    }

    private suspend fun refreshFinancials(userId: Int, moeda: String) = coroutineScope {
        val dashJob = async<NetworkResult<ResumoDashboard>> { repository.getResumoDashboard(userId) }
        val carteiraJob = async<NetworkResult<CarteiraResponse>> { repository.getCarteira(userId, moeda) }
        val histJob = async<NetworkResult<List<OrdemExecutada>>> { repository.getHistoricoOrdens(userId) }
        val cartJob = async<NetworkResult<List<CarrinhoItem>>> { repository.listarCarrinho(userId) }
        val auditoriaJob = async<NetworkResult<AuditoriaResponse>> { repository.getAuditoriaMercado() }
        val newsJob = async<NetworkResult<List<Noticia>>> { repository.getNoticias() }
        
        val dashResult = dashJob.await()
        val cartResult = carteiraJob.await()
        val histResult = histJob.await()
        val cartItemsResult = cartJob.await()
        val audResult = auditoriaJob.await()
        val newsResult = newsJob.await()

        val carteira = (cartResult as? NetworkResult.Success<CarteiraResponse>)?.data
        val posicoes = carteira?.posicoes ?: emptyList()
        val plTotal = posicoes.sumOf { it.lucro_prejuizo }
        val historico = (histResult as? NetworkResult.Success<List<OrdemExecutada>>)?.data ?: emptyList()
        val auditoriaLista = (audResult as? NetworkResult.Success<AuditoriaResponse>)?.data?.recomendacoes ?: emptyList()
        val noticias = (newsResult as? NetworkResult.Success<List<Noticia>>)?.data ?: emptyList()

        val perfil = _uiState.value.selectedUser?.perfil_risco ?: "Moderado"
        // Normalização do perfil para bater com o mapa do servidor (ex: "AGRESSIVO" -> "Agressivo")
        val perfilFmt = perfil.lowercase().split("_").last().replaceFirstChar { it.uppercase() }

        // 👇 FILTRAGEM POR JURISDIÇÃO (BRASIL vs EUA)
        val filteredAuditoria = auditoriaLista.filter { item ->
            val ticker = item.ativo ?: ""
            val isEstrangeiro = !(ticker.contains(Regex("\\d")) || ticker.endsWith(".SA", ignoreCase = true))
            if (moeda == "USD") isEstrangeiro else !isEstrangeiro
        }
        
        val filteredHistorico = historico.filter { item ->
            val ticker = item.ticker ?: ""
            val isEstrangeiro = !(ticker.contains(Regex("\\d")) || ticker.endsWith(".SA", ignoreCase = true))
            if (moeda == "USD") isEstrangeiro else !isEstrangeiro
        }
        
        val cartItems = (cartItemsResult as? NetworkResult.Success<List<CarrinhoItem>>)?.data ?: emptyList()
        val filteredCarrinho = cartItems.filter { item ->
            val ticker = item.ticker ?: ""
            val isEstrangeiro = !(ticker.contains(Regex("\\d")) || ticker.endsWith(".SA", ignoreCase = true))
            if (moeda == "USD") isEstrangeiro else !isEstrangeiro
        }
        
        val filteredNoticias = noticias.filter { item ->
            val fonte = (item.fonte ?: "").lowercase()
            val text = (item.titulo + " " + item.resumo).lowercase()
            val isEstrangeira = fonte.contains("yahoo") || fonte.contains("cnbc") || text.contains("wall street") || text.contains("fed ")
            if (moeda == "USD") isEstrangeira else !isEstrangeira
        }

        val buys = filteredAuditoria.count { it.sinaisPerfil?.get(perfilFmt) == "COMPRA FORTE" }
        val sells = filteredAuditoria.count { it.sinaisPerfil?.get(perfilFmt) == "ALERTA DE VENDA" }
        val total = filteredAuditoria.size.toFloat()
        val avgZ = if(filteredAuditoria.isNotEmpty()) filteredAuditoria.sumOf { it.zScore ?: 0.0 } / filteredAuditoria.size else 0.0
        val avgVar = if(filteredAuditoria.isNotEmpty()) filteredAuditoria.sumOf { it.riscoVar ?: 0.0 } / filteredAuditoria.size else 0.0

        _uiState.update { state ->
            state.copy(
                dashboard = (dashResult as? NetworkResult.Success<ResumoDashboard>)?.data,
                carteira = carteira,
                historico = filteredHistorico,
                carrinho = filteredCarrinho,
                auditoria = filteredAuditoria, // CORRIGIDO: Passando a lista FILTRADA
                noticias = filteredNoticias,
                buyCount = buys,
                sellCount = sells,
                neutralCount = (total.toInt() - buys - sells),
                buyPct = if(total > 0) buys / total else 0f,
                sellPct = if(total > 0) sells / total else 0f,
                neutralPct = if(total > 0) (total - buys - sells) / total else 0f,
                avgZ = avgZ,
                avgVar = avgVar,
                totalPl = if (plTotal != 0.0) plTotal else (state.selectedUser?.lucro_acumulado ?: 0.0),
                isLoading = false
            )
        }
    }

    private suspend fun addLogOptimized(log: LogMercado) = withContext(Dispatchers.Default) {
        val ticker = log.ativo ?: ""
        val isEstrangeiro = !(ticker.contains(Regex("\\d")) || ticker.endsWith(".SA", ignoreCase = true))
        val currentMarket = marketSession.currentMarket.value
        val shouldKeep = if (currentMarket == "USD") isEstrangeiro else !isEstrangeiro

        if (!shouldKeep) return@withContext // Ignora ticks do mercado que não está selecionado
        
        val perfilBruto = _uiState.value.selectedUser?.perfil_risco ?: "Moderado"
        val perfilFormatado = perfilBruto.lowercase().split("_").last().replaceFirstChar { it.uppercase() }
        
        // Prioriza sinal do perfil, senão sinal geral, senão neutro
        val sinalExibicao = log.sinaisPerfil?.get(perfilFormatado) ?: log.sinal ?: "NEUTRO"
        val newLog = log.copy(sinalExibicao = sinalExibicao)
        
        _uiState.update { state ->
            val updatedLogs = (listOf(newLog) + state.logs).take(60)
            
            // 🚨 SINCRONIZAÇÃO DE GATILHO (Com base na lógica da Web)
            val isTrigger = sinalExibicao.contains("COMPRA") || sinalExibicao.contains("VENDA")
            val ultimoAlvo = if (isTrigger) newLog else state.ultimoAlvo
            
            val oportunidades = updatedLogs.filter { it.sinalExibicao.contains("COMPRA") }.mapNotNull { it.ativo }.distinct().take(3)

            state.copy(
                logs = updatedLogs,
                ultimoAlvo = ultimoAlvo,
                oportunidades = oportunidades
            )
        }
    }
}
