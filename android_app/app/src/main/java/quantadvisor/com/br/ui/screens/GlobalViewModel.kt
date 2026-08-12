package quantadvisor.com.br.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.MacroResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.PontoHistorico
import quantadvisor.com.br.data.model.ResumoDashboard
import quantadvisor.com.br.data.repository.MarketRepository
import quantadvisor.com.br.session.MarketSession
import javax.inject.Inject

sealed class GlobalUiState {
    object Loading : GlobalUiState()
    data class Success(
        val dashboard: ResumoDashboard?,
        val macro: MacroResponse?,
        val history: List<PontoHistorico> = emptyList()
    ) : GlobalUiState()
    data class Error(val message: String) : GlobalUiState()
}

@HiltViewModel
class GlobalViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository,
    val marketSession: MarketSession
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<GlobalUiState>(GlobalUiState.Loading)
    val uiState: StateFlow<GlobalUiState> = _uiState.asStateFlow()

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.value = GlobalUiState.Loading
            try {
                val uid = SecurityManager.getUsuarioId(getApplication())
                Log.d("QuantAdvisor", "Global: Carregando dados para o UID: $uid")
                
                if (uid == 0) {
                    _uiState.value = GlobalUiState.Error("Sessão inválida. Por favor, faça login novamente.")
                    return@launch
                }

                val macroDeferred = async<NetworkResult<MacroResponse>> { repository.getDashboardMacro() }
                val dashDeferred = async<NetworkResult<ResumoDashboard>> { repository.getResumoDashboard(uid) }
                val historyDeferred = async<NetworkResult<List<PontoHistorico>>> { repository.getDashboardHistorico(uid) }

                val macroResult = macroDeferred.await()
                val dashResult = dashDeferred.await()
                val historyResult = historyDeferred.await()

                Log.d("QuantAdvisor", "Global: Macro success=${macroResult is NetworkResult.Success<*>}, Dash success=${dashResult is NetworkResult.Success<*>}")

                if (macroResult is NetworkResult.Success<*> || dashResult is NetworkResult.Success<*>) {
                    val historyList = historyResult.getOrNull() ?: emptyList()
                    
                    _uiState.value = GlobalUiState.Success(
                        macro = (macroResult as? NetworkResult.Success<MacroResponse>)?.data,
                        dashboard = (dashResult as? NetworkResult.Success<ResumoDashboard>)?.data,
                        history = historyList
                    )
                } else {
                    _uiState.value = GlobalUiState.Error("Falha ao carregar dados globais do servidor.")
                }
            } catch (e: Exception) {
                _uiState.value = GlobalUiState.Error("Erro: ${e.message}")
            }
        }
    }
}
