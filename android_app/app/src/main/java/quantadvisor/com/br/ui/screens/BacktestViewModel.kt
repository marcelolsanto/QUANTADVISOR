package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.BacktestResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.TradeBacktest
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class BacktestUiState(
    val ticker: String = "PETR4",
    val isLoading: Boolean = false,
    val winRate: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val totalTrades: Int = 0,
    val trades: List<TradeBacktest> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class BacktestViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    fun onTickerChange(newTicker: String) {
        _uiState.update { it.copy(ticker = newTicker) }
    }

    fun rodarBacktest() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = repository.runBacktest(ticker)) {
                is NetworkResult.Success<*> -> {
                    val resp = (result as NetworkResult.Success<BacktestResponse>).data
                    if (resp.sucesso) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            winRate = resp.win_rate,
                            maxDrawdown = resp.max_drawdown,
                            totalTrades = resp.total_trades,
                            trades = resp.trades
                        )}
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = resp.erro ?: "Erro no backtest") }
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
}
