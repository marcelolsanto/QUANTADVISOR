package quantadvisor.com.br.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.ValidarCadastroRequest
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class OtpUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isValidationSuccess: Boolean = false
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpUiState())
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    fun validar(email: String, codigo: String) {
        if (codigo.length < 6) {
            _uiState.update { it.copy(errorMessage = "Digite os 6 dÃ­gitos do cÃ³digo.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = repository.validarCadastro(ValidarCadastroRequest(email, codigo))) {
                is NetworkResult.Success -> {
                    val response = result.data
                    if (response.sucesso) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            isValidationSuccess = true,
                            successMessage = "Conta ativada com sucesso!"
                        )}
                    } else {
                        _uiState.update { it.copy(
                            isLoading = false,
                            errorMessage = response.erro ?: "CÃ³digo invÃ¡lido ou expirado."
                        )}
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = "Falha de rede: ${result.message}"
                    )}
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }
    
    fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}