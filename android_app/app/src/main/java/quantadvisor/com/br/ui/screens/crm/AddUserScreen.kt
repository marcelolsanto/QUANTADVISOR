package quantadvisor.com.br.ui.screens.crm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserScreen(
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AddUserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onSuccess()
    }

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Novo Usuário", fontWeight = FontWeight.Bold, color = PrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryColor,
                unfocusedBorderColor = OutlineVariant,
                focusedLabelColor = PrimaryColor
            )

            OutlinedTextField(
                value = uiState.nome,
                onValueChange = { viewModel.onNomeChange(it) },
                label = { Text("Nome Completo") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = { viewModel.onEmailChange(it) },
                label = { Text("E-mail") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            OutlinedTextField(
                value = uiState.whatsapp,
                onValueChange = { viewModel.onWhatsappChange(it) },
                label = { Text("WhatsApp") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            OutlinedTextField(
                value = uiState.login,
                onValueChange = { viewModel.onLoginChange(it) },
                label = { Text("Login") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            OutlinedTextField(
                value = uiState.senha,
                onValueChange = { viewModel.onSenhaChange(it) },
                label = { Text("Senha Inicial") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            OutlinedTextField(
                value = uiState.saldoInicial,
                onValueChange = { viewModel.onSaldoChange(it) },
                label = { Text("Capital Inicial (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            Text("Perfil de Risco", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Conservador", "Moderado", "Arrojado", "Agressivo").forEach { p ->
                    FilterChip(
                        selected = uiState.perfilRisco == p,
                        onClick = { viewModel.onPerfilChange(p) },
                        label = { Text(p, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryColor,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            if (uiState.errorMessage != null) {
                Text(uiState.errorMessage!!, color = VendaColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.salvar() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                else Text("CRIAR CONTA VIRTUAL", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }
}
