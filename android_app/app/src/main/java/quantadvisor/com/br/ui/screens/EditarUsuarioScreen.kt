package quantadvisor.com.br.ui.screens

import quantadvisor.com.br.di.RetrofitClient
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarUsuarioScreen(
    user: UsuarioResumo,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: EditarUsuarioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(user) {
        viewModel.setUser(user)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onSaveSuccess()
    }

    var roleMenuExpanded by remember { mutableStateOf(false) }
    var perfilMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Editar UsuÃ¡rio",
                        color = PrimaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(24.dp))
                        Text("Dados Cadastrais", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }

                    uiState.errorMessage?.let {
                        Text(it, color = VendaColor, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    QAInput(label = "Nome Completo", value = uiState.nome, onValueChange = { viewModel.onNomeChange(it) })

                    QAInput(label = "WhatsApp", value = uiState.whatsapp, onValueChange = { viewModel.onWhatsappChange(it) })

                    QAInput(label = "E-mail", value = uiState.email, onValueChange = { viewModel.onEmailChange(it) })

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            QADropdown(
                                label = "NÃ­vel de Acesso",
                                value = if (uiState.role == "GESTOR") "Gestor" else "Cliente",
                                expanded = roleMenuExpanded,
                                onExpandedChange = { roleMenuExpanded = it },
                                leadingIcon = Icons.Default.AdminPanelSettings,
                                options = listOf("Gestor", "Cliente"),
                                onSelect = {
                                    viewModel.onRoleChange(if (it == "Gestor") "GESTOR" else "CLIENTE")
                                    roleMenuExpanded = false
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            QADropdown(
                                label = "Perfil de Risco",
                                value = uiState.perfilRisco,
                                expanded = perfilMenuExpanded,
                                onExpandedChange = { perfilMenuExpanded = it },
                                options = listOf("Conservador", "Moderado", "Arrojado", "Agressivo"),
                                onSelect = {
                                    viewModel.onPerfilRiscoChange(it)
                                    perfilMenuExpanded = false
                                }
                            )
                        }
                    }

                    QAInput(label = "Login (UsuÃ¡rio)", value = uiState.login, onValueChange = { viewModel.onLoginChange(it) })

                    QAInput(label = "Nova Senha (opcional)", value = uiState.senha, onValueChange = { viewModel.onSenhaChange(it) }, isPassword = true)

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.salvar() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = Color.White)
                        ) {
                            if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("SALVAR ALTERAÃ‡Ã•ES", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Button(
                            onClick = onBackClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = TextMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QAInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false
) {
    Column {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainerLowest,
                unfocusedContainerColor = SurfaceContainerLowest,
                focusedBorderColor = PrimaryColor,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QADropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                shape = RoundedCornerShape(8.dp),
                leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = TextMuted) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceContainerLowest,
                    unfocusedContainerColor = SurfaceContainerLowest,
                    focusedBorderColor = PrimaryColor,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.background(SurfaceContainer)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = TextPrimary) },
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}