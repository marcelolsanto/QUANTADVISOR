package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.MonteCarloResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

data class MonteCarloUiState(
    val ticker: String = "PETR4",
    val isLoading: Boolean = false,
    val result: Map<String, Double>? = null,
    val grafico: List<Map<String, Any>> = emptyList(),
    val densityPoints: List<Pair<Float, Float>> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class MonteCarloViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonteCarloUiState())
    val uiState: StateFlow<MonteCarloUiState> = _uiState.asStateFlow()

    fun onTickerChange(newTicker: String) {
        _uiState.update { it.copy(ticker = newTicker) }
    }

    fun rodarSimulacao() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null, result = null) }

        viewModelScope.launch {
            when (val result = repository.runMonteCarlo(ticker)) {
                is NetworkResult.Success<*> -> {
                    val resp = (result as NetworkResult.Success<MonteCarloResponse>).data
                    if (resp.sucesso) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            result = resp.projecao_1_ano,
                            grafico = resp.grafico,
                            densityPoints = generateGaussianPoints()
                        )}
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = resp.erro ?: "Erro na simulação") }
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }

    private fun generateGaussianPoints(): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        val mean = 0.0
        val stdDev = 1.0
        val step = 0.1
        var x = -4.0
        while (x <= 4.0) {
            val y = (1.0 / (stdDev * sqrt(2.0 * Math.PI))) * exp(-0.5 * ((x - mean) / stdDev).pow(2.0))
            points.add(Pair(x.toFloat(), y.toFloat()))
            x += step
        }
        return points
    }
}
