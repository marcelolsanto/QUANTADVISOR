package quantadvisor.com.br.ui.screens.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.GenericResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.NovaContaRequest
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class AddUserUiState(
    val nome: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val login: String = "",
    val senha: String = "",
    val saldoInicial: String = "",
    val perfilRisco: String = "Moderado",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AddUserViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddUserUiState())
    val uiState: StateFlow<AddUserUiState> = _uiState.asStateFlow()

    fun onNomeChange(v: String) = _uiState.update { it.copy(nome = v) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v) }
    fun onWhatsappChange(v: String) = _uiState.update { it.copy(whatsapp = v) }
    fun onLoginChange(v: String) = _uiState.update { it.copy(login = v) }
    fun onSenhaChange(v: String) = _uiState.update { it.copy(senha = v) }
    fun onSaldoChange(v: String) = _uiState.update { it.copy(saldoInicial = v) }
    fun onPerfilChange(v: String) = _uiState.update { it.copy(perfilRisco = v) }

    fun salvar() {
        val s = uiState.value
        if (s.nome.isBlank() || s.email.isBlank() || s.login.isBlank() || s.senha.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Preencha os campos obrigatórios") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val request = NovaContaRequest(
                nome_cliente = s.nome,
                email = s.email,
                whatsapp = s.whatsapp,
                login = s.login,
                senha = s.senha,
                perfil_risco = s.perfilRisco,
                saldo_inicial = s.saldoInicial.toDoubleOrNull() ?: 0.0
            )
            val result = repository.criarUsuario(request)
            if (result is NetworkResult.Success<*> && (result as NetworkResult.Success<GenericResponse>).data.sucesso) {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                val msg = if (result is NetworkResult.Error) result.message else "Falha ao criar"
                _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }
}
