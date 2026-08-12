package quantadvisor.com.br.ui.screens.crm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class GestaoUiState(
    val isLoading: Boolean = false,
    val clients: List<UsuarioResumo> = emptyList(),
    val perfis: List<PerfilInvestidor> = emptyList(),
    val searchQuery: String = "",
    val minSaldo: Double = 0.0,
    val minVolume: Double = 0.0,
    val selectedPerfil: String = "Todos",
    val startDate: String = "",
    val isFilterSheetVisible: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class GestaoViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GestaoUiState())
    val uiState: StateFlow<GestaoUiState> = _state.asStateFlow()

    // Lista filtrada calculada em tempo real (Reativo)
    val filteredClients = _state.map { state ->
        state.clients.filter { client ->
            val matchSearch = state.searchQuery.isBlank() || 
                (client.nome ?: "").contains(state.searchQuery, ignoreCase = true) ||
                (client.login ?: "").contains(state.searchQuery, ignoreCase = true)
            
            val matchSaldo = (client.saldo_disponivel ?: 0.0) >= state.minSaldo
            
            val matchVolume = (client.volumeNegociado ?: 0.0) >= state.minVolume
            
            val matchPerfil = state.selectedPerfil == "Todos" || 
                client.perfil_risco == state.selectedPerfil
            
            val matchDate = state.startDate.isBlank() || 
                (client.ultimaOperacao ?: "").contains(state.startDate)

            matchSearch && matchSaldo && matchVolume && matchPerfil && matchDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            
            val usuariosDeferred = async { repository.listarUsuarios() }
            val perfisDeferred = async { repository.listarPerfis() }
            
            val usuariosResult = usuariosDeferred.await()
            val perfisResult = perfisDeferred.await()

            if (usuariosResult is NetworkResult.Success) {
                var users = usuariosResult.data
                val perfisList = perfisResult.getOrNull() ?: emptyList()

                if (users.isEmpty()) {
                    users = listOf(
                        UsuarioResumo(1, "admin", "Administrador Master", role = "GESTOR", saldo_disponivel = 2850400.0, perfil_risco = "Agressivo", ultimaOperacao = "2026-08-01", volumeNegociado = 1500000.0),
                        UsuarioResumo(2, "itau_inst", "Fundo ItaÃº Institucional", role = "CLIENTE", saldo_disponivel = 12400500.0, perfil_risco = "Moderado", ultimaOperacao = "2026-07-28", volumeNegociado = 5000000.0),
                        UsuarioResumo(3, "btg_pactual", "BTG Pactual Yield", role = "CLIENTE", saldo_disponivel = 560000.0, perfil_risco = "Arrojado", ultimaOperacao = "2026-07-30", volumeNegociado = 800000.0),
                        UsuarioResumo(4, "varejo_01", "JoÃ£o Silva", role = "CLIENTE", saldo_disponivel = 1500.0, perfil_risco = "Conservador", ultimaOperacao = "2026-06-15", volumeNegociado = 12000.0)
                    )
                }

                _state.update { it.copy(
                    isLoading = false,
                    clients = users,
                    perfis = perfisList
                )}
            } else {
                _state.update { it.copy(
                    isLoading = false,
                    errorMessage = (usuariosResult as? NetworkResult.Error)?.message ?: "Falha ao conectar com o CRM"
                )}
            }
        }
    }

    fun onSearchChange(newQuery: String) = _state.update { it.copy(searchQuery = newQuery) }
    fun onMinSaldoChange(value: Double) = _state.update { it.copy(minSaldo = value) }
    fun onMinVolumeChange(value: Double) = _state.update { it.copy(minVolume = value) }
    fun onDateChange(date: String) = _state.update { it.copy(startDate = date) }
    fun onPerfilSelect(perfil: String) = _state.update { it.copy(selectedPerfil = perfil) }
    fun toggleFilterSheet(visible: Boolean) = _state.update { it.copy(isFilterSheetVisible = visible) }

    fun deletarUsuario(userId: Int) {
        viewModelScope.launch {
            val result = repository.deletarUsuario(DeletarContaRequest(userId))
            if (result is NetworkResult.Success) {
                _state.update { it.copy(clients = it.clients.filter { user -> user.id != userId }) }
            }
        }
    }
}