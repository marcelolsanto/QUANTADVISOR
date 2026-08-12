package quantadvisor.com.br.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.ItemLancamento
import quantadvisor.com.br.data.model.LoteFiscal
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

sealed class AccountingUiState {
    object Loading : AccountingUiState()
    data class Success(
        val lancamentos: List<ItemLancamento>,
        val lotes: List<LoteFiscal>,
        val selectedTab: Int = 0
    ) : AccountingUiState()
    data class Error(val message: String) : AccountingUiState()
}

@HiltViewModel
class AccountingViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<AccountingUiState>(AccountingUiState.Loading)
    val uiState: StateFlow<AccountingUiState> = _uiState.asStateFlow()

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.value = AccountingUiState.Loading
            val uid = SecurityManager.getUsuarioId(getApplication())
            
            val lancResult = repository.listarLancamentos(uid)
            val lotesResult = repository.listarLotes(uid)
            
            if (lancResult is NetworkResult.Success && lotesResult is NetworkResult.Success) {
                _uiState.value = AccountingUiState.Success(
                    lancamentos = lancResult.data,
                    lotes = lotesResult.data
                )
            } else {
                _uiState.value = AccountingUiState.Error("Falha ao carregar dados contÃ¡beis")
            }
        }
    }

    fun onTabSelected(tab: Int) {
        val currentState = _uiState.value
        if (currentState is AccountingUiState.Success) {
            _uiState.value = currentState.copy(selectedTab = tab)
        }
    }
}