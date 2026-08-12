package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class BatchOperationsUiState(
    val cartItems: List<CarrinhoItem> = emptyList(),
    val auditoria: AuditoriaResponse? = null,
    val isLoading: Boolean = false,
    val isExecuting: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class BatchOperationsViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchOperationsUiState())
    val uiState: StateFlow<BatchOperationsUiState> = _uiState.asStateFlow()

    fun loadData(userId: Int = -1) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val cartResult = repository.listarCarrinho(if (userId != -1) userId else null)
            val auditoriaResult = repository.getAuditoriaMercado()

            _uiState.update { state ->
                state.copy(
                    cartItems = cartResult.getOrNull() ?: emptyList(),
                    auditoria = auditoriaResult.getOrNull(),
                    isLoading = false
                )
            }
        }
    }

    fun executeBatch() {
        viewModelScope.launch {
            val ids = _uiState.value.cartItems.map { it.id }
            if (ids.isEmpty()) {
                _uiState.update { it.copy(message = "Carrinho vazio.") }
                return@launch
            }

            _uiState.update { it.copy(isExecuting = true, message = "Executando ordens em lote...") }
            
            val result = repository.limparCarrinho(ids)
            when (result) {
                is NetworkResult.Success<*> -> {
                    _uiState.update { it.copy(cartItems = emptyList(), isExecuting = false, message = "Lote executado com sucesso!") }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isExecuting = false, message = "Erro na execução: ${result.message}") }
                }
                else -> {}
            }
        }
    }
}
