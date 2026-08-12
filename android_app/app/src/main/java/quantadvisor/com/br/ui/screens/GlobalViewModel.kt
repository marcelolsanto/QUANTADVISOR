package quantadvisor.com.br.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.MacroResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.PontoHistorico
import quantadvisor.com.br.data.model.ResumoDashboard
import quantadvisor.com.br.data.repository.MarketRepository
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
    private val repository: MarketRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<GlobalUiState>(GlobalUiState.Loading)
    val uiState: StateFlow<GlobalUiState> = _uiState.asStateFlow()

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.value = GlobalUiState.Loading
            try {
                val macroDeferred = async { repository.getDashboardMacro() }
                val dashDeferred = async { repository.getResumoDashboard(0) }
                val historyDeferred = async { repository.getDashboardHistorico(0) }

                val macroResult = macroDeferred.await()
                val dashResult = dashDeferred.await()
                val historyResult = historyDeferred.await()

                if (macroResult is NetworkResult.Success || dashResult is NetworkResult.Success) {
                    var historyList = historyResult.getOrNull() ?: emptyList()
                    
                    // MOCK DE EMERGÃŠNCIA PARA GRÃFICO
                    if (historyList.isEmpty()) {
                        Log.w("QuantAdvisor", "API de HistÃ³rico vazia. Injetando dados de simulaÃ§Ã£o.")
                        historyList = listOf(
                            PontoHistorico("01/01", 1400000.0),
                            PontoHistorico("05/01", 1425000.0),
                            PontoHistorico("10/01", 1410000.0),
                            PontoHistorico("15/01", 1440000.0),
                            PontoHistorico("20/01", 1435000.0),
                            PontoHistorico("25/01", 1460000.0),
                            PontoHistorico("30/01", 1452890.0)
                        )
                    }

                    _uiState.value = GlobalUiState.Success(
                        macro = macroResult.getOrNull(),
                        dashboard = dashResult.getOrNull(),
                        history = historyList
                    )
                } else {
                    _uiState.value = GlobalUiState.Error("Falha ao carregar dados globais")
                }
            } catch (e: Exception) {
                _uiState.value = GlobalUiState.Error("Erro: ${e.message}")
            }
        }
    }
}