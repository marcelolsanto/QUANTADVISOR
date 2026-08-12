package quantadvisor.com.br.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.PontoCurvaCapital
import quantadvisor.com.br.data.model.ReplayDecisao
import quantadvisor.com.br.data.model.ResumoEstrategia
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

sealed class TearsheetUiState {
    object Loading : TearsheetUiState()
    data class Success(
        val resumo: ResumoEstrategia?,
        val curva: List<PontoCurvaCapital>,
        val replay: List<ReplayDecisao>,
        val selectedFund: String = "Alpha Fund Volatility"
    ) : TearsheetUiState()
    data class Error(val message: String) : TearsheetUiState()
}

@HiltViewModel
class TearsheetViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<TearsheetUiState>(TearsheetUiState.Loading)
    val uiState: StateFlow<TearsheetUiState> = _uiState.asStateFlow()

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.value = TearsheetUiState.Loading
            val uid = SecurityManager.getUsuarioId(getApplication())
            
            val respResumo = async { repository.getInstitucionalResumo(uid) }
            val respCurva = async { repository.getCurvaCapital(uid) }
            val respReplay = async { repository.getReplayDecisao(uid) }

            val resumoResult = respResumo.await()
            val curvaResult = respCurva.await()
            val replayResult = respReplay.await()

            if (resumoResult is NetworkResult.Success || curvaResult is NetworkResult.Success || replayResult is NetworkResult.Success) {
                _uiState.update { 
                    TearsheetUiState.Success(
                        resumo = resumoResult.getOrNull(),
                        curva = curvaResult.getOrNull() ?: emptyList(),
                        replay = replayResult.getOrNull() ?: emptyList()
                    )
                }
            } else {
                _uiState.value = TearsheetUiState.Error("Erro ao carregar tearsheet")
            }
        }
    }

    fun onFundSelected(fund: String) {
        val currentState = _uiState.value
        if (currentState is TearsheetUiState.Success) {
            _uiState.update { (it as TearsheetUiState.Success).copy(selectedFund = fund) }
        }
        carregarDados()
    }
}