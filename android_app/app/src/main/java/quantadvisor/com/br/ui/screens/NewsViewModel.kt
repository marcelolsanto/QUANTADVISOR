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
                is NetworkResult.Success<*> -> {
                    val finalNews = (result as NetworkResult.Success<List<Noticia>>).data
                    _state.update { it.copy(news = finalNews, isLoading = false) }
                }
                is NetworkResult.Error -> {
                    _state.update { it.copy(errorMessage = "Erro ao carregar notícias: ${result.message}", isLoading = false) }
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
