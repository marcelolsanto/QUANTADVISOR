package quantadvisor.com.br.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketSession @Inject constructor() {
    private val _currentMarket = MutableStateFlow("BRL")
    val currentMarket: StateFlow<String> = _currentMarket.asStateFlow()

    fun setMarket(moeda: String) {
        if (moeda == "BRL" || moeda == "USD") {
            _currentMarket.value = moeda
        }
    }
}
