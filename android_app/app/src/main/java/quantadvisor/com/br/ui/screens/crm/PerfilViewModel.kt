package quantadvisor.com.br.ui.screens.crm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class PerfilUiState(
    val user: UsuarioResumo? = null,
    val isLoading: Boolean = false,
    val isLogoutSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PerfilViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PerfilUiState())
    val uiState: StateFlow<PerfilUiState> = _uiState.asStateFlow()

    init {
        carregarPerfil()
    }

    fun carregarPerfil() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val uid = SecurityManager.getUsuarioId(getApplication())
                Log.d("QuantAdvisor", "Perfil: Carregando UID $uid")
                val result = repository.obterInfoUsuario(uid)
                if (result is NetworkResult.Success) {
                    Log.d("QuantAdvisor", "Perfil: Sucesso ao carregar usuário ${result.data.nome}")
                    _uiState.update { it.copy(user = result.data, isLoading = false) }
                } else {
                    Log.e("QuantAdvisor", "Perfil: Erro ao carregar perfil")
                    _uiState.update { it.copy(errorMessage = "Erro ao carregar perfil", isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("QuantAdvisor", "Perfil: Crash ao carregar", e)
                _uiState.update { it.copy(errorMessage = "Erro de conexão", isLoading = false) }
            }
        }
    }

    fun logout() {
        SecurityManager.limparSessao(getApplication())
        _uiState.update { it.copy(isLogoutSuccess = true) }
    }
}
