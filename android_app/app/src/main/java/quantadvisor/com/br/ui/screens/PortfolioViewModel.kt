package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.AtivoPatrimonio
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class PortfolioUiState(
    val user: UsuarioResumo? = null,
    val assets: List<AtivoPatrimonio> = emptyList(),
    val totalEquity: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    fun loadPortfolio(userId: Int = -1) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val userResult = repository.obterInfoUsuario(if (userId != -1) userId else null)
            val carteiraResult = repository.getCarteira(if (userId != -1) userId else null)

            _uiState.update { state ->
                state.copy(
                    user = userResult.getOrNull(),
                    assets = carteiraResult.getOrNull()?.posicoes ?: emptyList(),
                    // Calculando patrimÃ´nio total a partir das posiÃ§Ãµes MtM + Saldo
                    totalEquity = (carteiraResult.getOrNull()?.saldo_brl ?: 0.0) + 
                                  (carteiraResult.getOrNull()?.posicoes?.sumOf { it.lucro_prejuizo + (it.quantidade * it.preco_medio) } ?: 0.0),
                    isLoading = false,
                    error = (carteiraResult as? NetworkResult.Error)?.message
                )
            }
        }
    }
}