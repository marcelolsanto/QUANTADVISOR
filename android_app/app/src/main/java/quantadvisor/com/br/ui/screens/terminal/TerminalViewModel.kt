package quantadvisor.com.br.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.repository.MarketEvent
import quantadvisor.com.br.data.repository.MarketRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class LogMercado(
    val id: String = UUID.randomUUID().toString(),
    val hora: String = "",
    val ativo: String,
    val sinal: String,
    @SerializedName("preco_atual") val precoAtual: Double?,
    @SerializedName("z_score") val zScore: Double?,
    @SerializedName("risco_var") val riscoVar: Double?,
    val fonte: String? = "YAHOO"
)

data class TerminalUiState(
    val selectedUser: UsuarioResumo? = null,
    val dashboard: ResumoDashboard? = null,
    val carteira: CarteiraResponse? = null,
    val isLoading: Boolean = true,
    val isConnected: Boolean = false,
    val logs: List<LogMercado> = emptyList(),
    val allUsers: List<UsuarioResumo> = emptyList(),
    val orderStatus: String? = null,
    val isSendingOrder: Boolean = false,
    val pilotoAutomatico: Boolean = true,
    val regime: String = "BEAR MARKET (Crise)",
    val drawdown: Double = -0.57
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        observeMarket()
        startMockMarketFlow()
    }

    fun initTerminal(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val userResult = repository.obterInfoUsuario(if (userId != -1) userId else null)
            
            when (userResult) {
                is NetworkResult.Success -> {
                    val user = userResult.data
                    _uiState.update { it.copy(
                        selectedUser = user,
                        pilotoAutomatico = user.piloto_automatico ?: true
                    ) }

                    val dashJob = async { repository.getResumoDashboard(user.id) }
                    val carteiraJob = async { repository.getCarteira(user.id) }
                    
                    val dashResult = dashJob.await()
                    val cartResult = carteiraJob.await()

                    _uiState.update { state ->
                        state.copy(
                            dashboard = dashResult.getOrNull(),
                            carteira = cartResult.getOrNull(),
                            isLoading = false
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, orderStatus = "Erro ao carregar perfil: ${userResult.message}") }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }

            val usersResult = repository.listarUsuarios()
            if (usersResult is NetworkResult.Success) {
                _uiState.update { it.copy(allUsers = usersResult.data) }
            }
        }
    }

    fun togglePiloto() {
        val current = _uiState.value.pilotoAutomatico
        val userId = _uiState.value.selectedUser?.id ?: return
        
        viewModelScope.launch {
            val result = repository.togglePilotoAutomatico(TogglePilotoReq(userId, !current))
            if (result is NetworkResult.Success) {
                _uiState.update { it.copy(pilotoAutomatico = !current) }
            }
        }
    }

    fun circuitBreaker() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingOrder = true, orderStatus = "Acionando Circuit Breaker...") }
            delay(2000)
            _uiState.update { it.copy(isSendingOrder = false, orderStatus = "CUSTÃ“DIA ZERADA COM SUCESSO!") }
            delay(3000)
            _uiState.update { it.copy(orderStatus = null) }
        }
    }

    fun enviarOrdem(ticker: String, tipo: String, qtd: Int, preco: Double) {
        val userId = uiState.value.selectedUser?.id ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingOrder = true, orderStatus = "Processando ordem...") }
            
            val request = OrdemRequest(
                usuario_id = userId,
                ticker = ticker,
                quantidade = qtd,
                preco = preco,
                tipo_ordem = tipo
            )
            
            when (val result = repository.enviarOrdem(request)) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(isSendingOrder = false, orderStatus = "Ordem enviada com sucesso!") }
                    delay(3000)
                    _uiState.update { it.copy(orderStatus = null) }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isSendingOrder = false, orderStatus = "Erro: ${result.message}") }
                    delay(5000)
                    _uiState.update { it.copy(orderStatus = null) }
                }
                NetworkResult.Loading -> {
                    // JÃ¡ estÃ¡ em loading
                }
            }
        }
    }

    private fun observeMarket() {
        repository.listenMarketFlow()
            .onEach { event ->
                when (event) {
                    is MarketEvent.Data -> addLog(event.log)
                    is MarketEvent.Status -> _uiState.update { it.copy(isConnected = event.isConnected) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun addLog(log: LogMercado) {
        val h = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = log.copy(hora = h)
        _uiState.update { state ->
            state.copy(logs = (listOf(newLog) + state.logs).take(50))
        }
    }

    private fun startMockMarketFlow() {
        viewModelScope.launch {
            val ativos = listOf("PETR4", "VALE3", "ITUB4", "AAPL", "NVDA", "BTC/USD")
            val sinais = listOf("COMPRA FORTE", "VENDA ESTRUTURADA", "NEUTRO")
            while(true) {
                delay(3000)
                if (!uiState.value.isConnected || uiState.value.logs.isEmpty()) {
                    val mockLog = LogMercado(
                        ativo = ativos.random(),
                        sinal = sinais.random(),
                        precoAtual = (10..500).random().toDouble() + Math.random(),
                        zScore = ((-300..300).random().toDouble() / 100.0),
                        riscoVar = -2.5
                    )
                    addLog(mockLog)
                }
            }
        }
    }
}