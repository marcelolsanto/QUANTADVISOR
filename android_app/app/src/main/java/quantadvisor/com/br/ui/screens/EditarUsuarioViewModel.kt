package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.EditarContaRequest
import quantadvisor.com.br.data.model.GenericResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class EditarUsuarioUiState(
    val user: UsuarioResumo? = null,
    val nome: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val login: String = "",
    val senha: String = "",
    val role: String = "CLIENTE",
    val perfilRisco: String = "Moderado",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class EditarUsuarioViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditarUsuarioUiState())
    val uiState: StateFlow<EditarUsuarioUiState> = _uiState.asStateFlow()

    fun setUser(user: UsuarioResumo) {
        _uiState.update { it.copy(
            user = user,
            nome = user.nome ?: "",
            email = user.email ?: "",
            whatsapp = user.whatsapp ?: user.celular ?: "",
            login = user.login ?: "",
            role = user.role ?: "CLIENTE",
            perfilRisco = user.perfil_risco ?: "Moderado"
        )}
    }

    fun onNomeChange(v: String) = _uiState.update { it.copy(nome = v) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v) }
    fun onWhatsappChange(v: String) = _uiState.update { it.copy(whatsapp = v) }
    fun onLoginChange(v: String) = _uiState.update { it.copy(login = v) }
    fun onSenhaChange(v: String) = _uiState.update { it.copy(senha = v) }
    fun onRoleChange(v: String) = _uiState.update { it.copy(role = v) }
    fun onPerfilRiscoChange(v: String) = _uiState.update { it.copy(perfilRisco = v) }

    fun salvar() {
        val u = uiState.value
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val req = EditarContaRequest(
                    id = u.user?.id ?: 0,
                    nome_cliente = u.nome,
                    email = u.email,
                    whatsapp = u.whatsapp,
                    login = u.login,
                    perfil_risco = u.perfilRisco,
                    senha = u.senha.takeIf { it.isNotBlank() },
                    role = u.role
                )
                val result = repository.editarUsuario(req)
                if (result is NetworkResult.Success<*> && (result as NetworkResult.Success<GenericResponse>).data.sucesso) {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                } else {
                    val msg = if (result is NetworkResult.Error) result.message else "Erro ao salvar"
                    _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro de conexão") }
            }
        }
    }
}
