package quantadvisor.com.br.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.data.repository.MarketRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed class ComplianceUiState {
    object Loading : ComplianceUiState()
    data class Success(
        val fiscalResumo: ResumoFiscalMensal?,
        val selectedAnoMes: String
    ) : ComplianceUiState()
    data class Error(val message: String) : ComplianceUiState()
}

@HiltViewModel
class ComplianceViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ComplianceUiState>(ComplianceUiState.Loading)
    val uiState: StateFlow<ComplianceUiState> = _uiState.asStateFlow()

    private var currentAnoMes = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.value = ComplianceUiState.Loading
            val uid = SecurityManager.getUsuarioId(getApplication())
            val result = repository.getResumoFiscal(uid, currentAnoMes)
            
            when (result) {
                is NetworkResult.Success<*> -> {
                    _uiState.value = ComplianceUiState.Success((result as NetworkResult.Success<ResumoFiscalMensal>).data, currentAnoMes)
                }
                is NetworkResult.Error -> {
                    _uiState.value = ComplianceUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }
    
    fun setAnoMes(anoMes: String) {
        currentAnoMes = anoMes
        carregarDados()
    }
}
