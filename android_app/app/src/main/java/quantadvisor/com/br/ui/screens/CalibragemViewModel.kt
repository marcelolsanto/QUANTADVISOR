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
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class CalibragemUiState(
    val user: UsuarioResumo? = null,
    val kellyFraction: Float = 25f,
    val maxConcentration: Float = 15f,
    val takeProfit: Float = 12f,
    val stopLoss: Double = -5.0,
    val isPilotActive: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CalibragemViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CalibragemUiState())
    val uiState: StateFlow<CalibragemUiState> = _uiState.asStateFlow()

    fun setUser(user: UsuarioResumo?) {
        _uiState.update { it.copy(user = user, isPilotActive = user?.piloto_automatico ?: false) }
        carregarParametros()
    }

    private fun carregarParametros() {
        val uid = _uiState.value.user?.id ?: SecurityManager.getUsuarioId(getApplication())
        viewModelScope.launch {
            val result = repository.getParametros(uid)
            if (result is NetworkResult.Success<*>) {
                val p = (result as NetworkResult.Success<ParametrosOperacionais>).data
                _uiState.update { it.copy(
                    kellyFraction = (p.multiplicador_kelly * 100).toFloat(),
                    maxConcentration = (p.limite_concentracao_ativo * 100).toFloat(),
                    takeProfit = (p.gatilho_rebalanceamento * 100).toFloat(),
                    stopLoss = p.piso_max_drawdown
                )}
            }
        }
    }

    fun onKellyChange(v: Float) = _uiState.update { it.copy(kellyFraction = v) }
    fun onMaxConcentrationChange(v: Float) = _uiState.update { it.copy(maxConcentration = v) }
    fun onTakeProfitChange(v: Float) = _uiState.update { it.copy(takeProfit = v) }

    fun togglePiloto(active: Boolean) {
        val uid = _uiState.value.user?.id ?: SecurityManager.getUsuarioId(getApplication())
        viewModelScope.launch {
            val result = repository.togglePilotoAutomatico(TogglePilotoReq(uid, active))
            if (result is NetworkResult.Success<*>) {
                _uiState.update { it.copy(isPilotActive = active) }
            }
        }
    }

    fun salvar() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val uid = _uiState.value.user?.id ?: SecurityManager.getUsuarioId(getApplication())
                val req = ParametrosOperacionais(
                    usuario_id = uid,
                    multiplicador_kelly = _uiState.value.kellyFraction.toDouble() / 100.0,
                    limite_concentracao_ativo = _uiState.value.maxConcentration.toDouble() / 100.0,
                    gatilho_rebalanceamento = _uiState.value.takeProfit.toDouble() / 100.0,
                    piso_max_drawdown = _uiState.value.stopLoss,
                    multiplicador_stop_var = 1.5,
                    z_score_compra_forte = -1.5,
                    z_score_venda_lucro = 1.5,
                    z_score_stop_loss = -2.0,
                    bloqueio_sentimento_negativo = true,
                    custo_friccao_padrao = 0.0003,
                    modo_isencao_fiscal_estrita = true
                )
                val result = repository.configurarRobo(req)
                if (result is NetworkResult.Success<*> && (result as NetworkResult.Success<GenericResponse>).data.sucesso) {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = (result as? NetworkResult.Error)?.message ?: "Erro ao salvar") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Falha de rede") }
            }
        }
    }
}
