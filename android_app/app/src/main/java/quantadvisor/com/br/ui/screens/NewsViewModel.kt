package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.Noticia
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class NewsUiState(
    val isLoading: Boolean = false,
    val news: List<Noticia> = emptyList(),
    val filter: String = "Todas",
    val errorMessage: String? = null
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _state.asStateFlow()

    val filteredNews = _state.map { state ->
        if (state.filter == "Todas") state.news 
        else state.news.filter { it.impacto == state.filter.uppercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        carregarNoticias()
    }

    fun carregarNoticias() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getNoticias()) {
                is NetworkResult.Success -> {
                    var finalNews = result.data
                    if (finalNews.isEmpty()) {
                        finalNews = listOf(
                            Noticia("1", "Copom mantÃ©m taxa Selic a 10.50%", "O comitÃª destacou que a inflaÃ§Ã£o permanece em nÃ­veis de alerta, exigindo cautela na polÃ­tica monetÃ¡ria.", "Valor EconÃ´mico", "10:30", "ALTO"),
                            Noticia("2", "Nvidia reporta lucros recordes", "A gigante dos chips superou as expectativas de Wall Street impulsionada pela demanda de IA.", "Bloomberg", "09:15", "MÃ‰DIO"),
                            Noticia("3", "Ibovespa opera em leve alta", "Mercado reage positivamente aos dados de emprego vindos dos EUA.", "InfoMoney", "11:45", "BAIXO")
                        )
                    }
                    _state.update { it.copy(news = finalNews, isLoading = false) }
                }
                is NetworkResult.Error -> {
                    _state.update { it.copy(errorMessage = "Erro ao carregar notÃ­cias: ${result.message}", isLoading = false) }
                }
                NetworkResult.Loading -> {
                    _state.update { it.copy(isLoading = true) }
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _state.update { it.copy(filter = filter) }
    }
}