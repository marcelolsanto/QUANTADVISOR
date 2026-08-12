package quantadvisor.com.br.ui.screens.login

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.OtpValidationScreen
import quantadvisor.com.br.data.model.NovaContaRequest
import quantadvisor.com.br.ui.theme.*

enum class AuthView {
    LOGIN, REGISTER, OTP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Controle de Navegação Interna
    var currentView by remember { mutableStateOf(AuthView.LOGIN) }

    // Estados de Login
    var login by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    // Estados de Registo
    var regNome by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regWhats by remember { mutableStateOf("") }
    var regLogin by remember { mutableStateOf("") }
    var regSenha by remember { mutableStateOf("") }
    var regCapital by remember { mutableStateOf("") }
    var regPerfil by remember { mutableStateOf("Moderado") }
    var menuPerfilExpandido by remember { mutableStateOf(false) }

    // Efeito para sucesso de login
    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) onLoginSuccess()
    }

    // Efeito para sucesso de solicitação de cadastro
    LaunchedEffect(uiState.isRegisterRequestSuccess) {
        if (uiState.isRegisterRequestSuccess) {
            currentView = AuthView.OTP
        }
    }

    // Estilo global dos TextFields
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = InfoColor,
        unfocusedBorderColor = BorderColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = InputBgColor,
        unfocusedContainerColor = InputBgColor,
        cursorColor = InfoColor
    )

    Crossfade(
        targetState = currentView,
        animationSpec = tween(durationMillis = 400),
        label = "Auth Transitions"
    ) { view ->
        when (view) {
            AuthView.LOGIN, AuthView.REGISTER -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgBackground)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBgColor, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // CABEÇALHO (LOGO)
                        Text(
                            text = "QuantAdvisor",
                            color = InfoColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (view == AuthView.LOGIN) "Gestão Patrimonial e IA Institucional" else "Abertura de Conta",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 24.dp),
                            textAlign = TextAlign.Center
                        )

                        uiState.error?.let { msg ->
                            Text(
                                text = "⚠️ $msg",
                                color = VendaColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .background(VendaColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .border(1.dp, VendaColor, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }

                        // VIEW: LOGIN
                        if (view == AuthView.LOGIN) {
                            OutlinedTextField(
                                value = login,
                                onValueChange = { login = it },
                                label = { Text("Login", color = TextMuted) },
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            )
                            OutlinedTextField(
                                value = senha,
                                onValueChange = { senha = it },
                                label = { Text("Senha", color = TextMuted) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                            )
                            Button(
                                onClick = { viewModel.login(login, senha) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InfoColor)
                            ) {
                                if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                else Text("ENTRAR NO SISTEMA", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Novo por aqui? ", color = TextMuted, fontSize = 14.sp)
                                Text(
                                    text = "Abra a sua conta.",
                                    color = InfoColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { 
                                        currentView = AuthView.REGISTER
                                        viewModel.clearError() 
                                    }
                                )
                            }
                        }

                        // VIEW: REGISTER
                        if (view == AuthView.REGISTER) {
                            OutlinedTextField(
                                value = regNome,
                                onValueChange = { regNome = it },
                                placeholder = { Text("Nome Completo", color = TextMuted) },
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                placeholder = { Text("Email Institucional", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = regWhats,
                                onValueChange = { regWhats = it },
                                placeholder = { Text("Telemóvel (WhatsApp)", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = regLogin,
                                onValueChange = { regLogin = it },
                                placeholder = { Text("Nome de Utilizador (Login)", color = TextMuted) },
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = regSenha,
                                onValueChange = { regSenha = it },
                                placeholder = { Text("Senha", color = TextMuted) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = inputColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ExposedDropdownMenuBox(
                                    expanded = menuPerfilExpandido,
                                    onExpandedChange = { menuPerfilExpandido = !menuPerfilExpandido },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = regPerfil,
                                        onValueChange = {},
                                        readOnly = true,
                                        colors = inputColors,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = menuPerfilExpandido,
                                        onDismissRequest = { menuPerfilExpandido = false },
                                        modifier = Modifier.background(CardBgColor)
                                    ) {
                                        listOf("Conservador", "Moderado", "Arrojado", "Agressivo").forEach { selectionOption ->
                                            DropdownMenuItem(
                                                text = { Text(selectionOption, color = TextPrimary) },
                                                onClick = { regPerfil = selectionOption; menuPerfilExpandido = false }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = regCapital,
                                    onValueChange = { regCapital = it },
                                    placeholder = { Text("Capital (R$)", color = TextMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = inputColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    if (regNome.isBlank() || regEmail.isBlank() || regWhats.isBlank() || regLogin.isBlank() || regSenha.isBlank()) {
                                        viewModel.login("", "") // Forçar erro de preenchimento
                                        return@Button
                                    }
                                    val request = NovaContaRequest(
                                        nome_cliente = regNome,
                                        email = regEmail,
                                        whatsapp = regWhats,
                                        login = regLogin,
                                        senha = regSenha,
                                        perfil_risco = regPerfil,
                                        saldo_inicial = regCapital.toDoubleOrNull() ?: 0.0
                                    )
                                    viewModel.registrar(request)
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InfoColor)
                            ) {
                                if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                else Text("RECEBER CÓDIGO NO WHATSAPP", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Já tem conta? ", color = TextMuted, fontSize = 14.sp)
                                Text(
                                    text = "Fazer Login.",
                                    color = InfoColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { 
                                        currentView = AuthView.LOGIN
                                        viewModel.clearError() 
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AuthView.OTP -> {
                OtpValidationScreen(
                    email = regEmail,
                    codigoDevRecebido = uiState.otpCode,
                    onValidationSuccess = {
                        login = regLogin
                        senha = ""
                        currentView = AuthView.LOGIN
                        viewModel.clearError()
                        viewModel.resetRegisterState()
                    },
                    onResendCode = {
                        val request = NovaContaRequest(
                            nome_cliente = regNome,
                            email = regEmail,
                            whatsapp = regWhats,
                            login = regLogin,
                            senha = regSenha,
                            perfil_risco = regPerfil,
                            saldo_inicial = regCapital.toDoubleOrNull() ?: 0.0
                        )
                        viewModel.registrar(request)
                    },
                    onBackClick = {
                        currentView = AuthView.REGISTER
                        viewModel.clearError()
                        viewModel.resetRegisterState()
                    }
                )
            }
        }
    }
}