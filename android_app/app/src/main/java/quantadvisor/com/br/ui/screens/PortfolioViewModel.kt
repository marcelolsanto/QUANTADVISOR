package quantadvisor.com.br.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.AtivoPatrimonio
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.data.model.CarteiraResponse
import quantadvisor.com.br.data.repository.MarketRepository
import quantadvisor.com.br.session.MarketSession
import javax.inject.Inject

data class PortfolioUiState(
    val user: UsuarioResumo? = null,
    val assets: List<AtivoPatrimonio> = emptyList(),
    val totalEquity: Double = 0.0,
    val totalPl: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository,
    val marketSession: MarketSession
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            marketSession.currentMarket.collectLatest { moedaAtiva ->
                loadPortfolio(moeda = moedaAtiva)
            }
        }
    }

    fun loadPortfolio(userId: Int = -1, moeda: String = "BRL") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val uid = if (userId == -1) SecurityManager.getUsuarioId(getApplication()) else userId
            
            val userResult = repository.obterInfoUsuario(uid)
            val carteiraResult = repository.getCarteira(uid, moeda)

            val carteira = (carteiraResult as? NetworkResult.Success)?.data
            val posicoes = carteira?.posicoes ?: emptyList()
            
            // CÁLCULO REAL DO P&L (LUCRO/PREJUÍZO)
            val plTotal = posicoes.sumOf { it.lucro_prejuizo }
            
            // CÁLCULO DO PATRIMÔNIO TOTAL MtM
            val mtmTotal = posicoes.sumOf { (it.quantidade * it.preco_medio) + it.lucro_prejuizo }
            val saldoDisponivel = if (moeda == "USD") (carteira?.saldo_usd ?: 0.0) else (carteira?.saldo_brl ?: 0.0)

            _uiState.update { state ->
                state.copy(
                    user = userResult.getOrNull(),
                    assets = posicoes,
                    totalEquity = saldoDisponivel + mtmTotal,
                    totalPl = if (plTotal != 0.0) plTotal else (userResult.getOrNull()?.lucro_acumulado ?: 0.0),
                    isLoading = false,
                    error = (carteiraResult as? NetworkResult.Error)?.message
                )
            }
        }
    }
}
