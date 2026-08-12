package quantadvisor.com.br.ui.screens.login

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
import quantadvisor.com.br.data.model.LoginRequest
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.NovaContaRequest
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginSuccess: Boolean = false,
    val isRegisterRequestSuccess: Boolean = false,
    val otpCode: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val repository: MarketRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(login: String, senha: String) {
        if (login.isBlank() || senha.isBlank()) {
            _uiState.update { it.copy(error = "Por favor, preencha todos os campos.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = repository.login(LoginRequest(login, senha))) {
                is NetworkResult.Success -> {
                    val response = result.data
                    if (response.sucesso && response.token != null) {
                        SecurityManager.salvarSessao(
                            getApplication(),
                            response.token,
                            response.usuario_id ?: 0,
                            response.nome ?: "UsuÃ¡rio",
                            response.role ?: "CLIENTE"
                        )
                        _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = response.erro ?: "Credenciais invÃ¡lidas") }
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = "Erro de conexÃ£o: ${result.message}") }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }

    fun registrar(request: NovaContaRequest) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = repository.solicitarCadastro(request)) {
                is NetworkResult.Success -> {
                    val response = result.data
                    if (response.sucesso) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            isRegisterRequestSuccess = true,
                            otpCode = response.codigo_teste // Apenas para ambiente de desenvolvimento
                        )}
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = response.erro ?: "Erro ao solicitar cadastro") }
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = "Falha de rede: ${result.message}") }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetRegisterState() {
        _uiState.update { it.copy(isRegisterRequestSuccess = false, otpCode = null) }
    }
}