package quantadvisor.com.br.ui.screens.crm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CentralGestaoScreen(
    onEditUser: (UsuarioResumo) -> Unit,
    onCalibrateUser: (UsuarioResumo) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToTerminal: (UsuarioResumo) -> Unit,
    onNavigateToGlobal: () -> Unit,
    viewModel: GestaoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredClients by viewModel.filteredClients.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onNavigateToGlobal() }) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = PrimaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text("QuantAdvisor", color = PrimaryColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.carregarDados() }) { Icon(Icons.Default.Refresh, "Atualizar", tint = PrimaryColor) }
                    Box(
                        modifier = Modifier.padding(end = 12.dp).size(32.dp).clip(CircleShape).background(PrimaryColor).clickable { onNavigateToProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color(0xFF002E6A), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Central de GestÃ£o", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { viewModel.toggleFilterSheet(true) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceContainerHighest)
                ) {
                    Icon(Icons.Default.FilterList, "Filtros", tint = if(uiState.minSaldo > 0 || uiState.selectedPerfil != "Todos") CompraColor else PrimaryColor)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchChange(it) },
                placeholder = { Text("Buscar por nome ou @login...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceContainer, unfocusedContainerColor = SurfaceContainer, focusedBorderColor = PrimaryColor)
            )

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PrimaryColor) }
            } else if (filteredClients.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum usuÃ¡rio encontrado.", color = TextMuted) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredClients, key = { it.id }) { client ->
                        ClientCard(
                            client = client,
                            onEdit = { onEditUser(client) },
                            onCalibrate = { onCalibrateUser(client) },
                            onTerminal = { onNavigateToTerminal(client) },
                            onDelete = { viewModel.deletarUsuario(client.id) }
                        )
                    }
                }
            }
        }

        // Bottom Sheet de Filtros AvanÃ§ados
        if (uiState.isFilterSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.toggleFilterSheet(false) },
                sheetState = sheetState,
                containerColor = SurfaceContainer
            ) {
                Column(Modifier.padding(24.dp).padding(bottom = 40.dp).verticalScroll(rememberScrollState())) {
                    Text("Filtros AvanÃ§ados", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    
                    Text("Saldo MÃ­nimo: R$ ${String.format(Locale.GERMANY, "%,.0f", uiState.minSaldo)}", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = uiState.minSaldo.toFloat(),
                        onValueChange = { viewModel.onMinSaldoChange(it.toDouble()) },
                        valueRange = 0f..1000000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryColor, activeTrackColor = PrimaryColor)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("Volume Negociado MÃ­nimo: R$ ${String.format(Locale.GERMANY, "%,.0f", uiState.minVolume)}", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = uiState.minVolume.toFloat(),
                        onValueChange = { viewModel.onMinVolumeChange(it.toDouble()) },
                        valueRange = 0f..5000000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryColor, activeTrackColor = PrimaryColor)
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    Text("Data da Ãšltima OperaÃ§Ã£o (AAAA-MM)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.startDate,
                        onValueChange = { viewModel.onDateChange(it) },
                        placeholder = { Text("Ex: 2026-07", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor)
                    )

                    Spacer(Modifier.height(24.dp))
                    Text("Perfil de Risco", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Todos", "Conservador", "Moderado", "Arrojado", "Agressivo").forEach { perfil ->
                            FilterChip(
                                selected = uiState.selectedPerfil == perfil,
                                onClick = { viewModel.onPerfilSelect(perfil) },
                                label = { Text(perfil) },
                                colors = FilterChipDefaults.filterChipColors(labelColor = TextMuted, selectedLabelColor = Color.Black, selectedContainerColor = PrimaryColor)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.toggleFilterSheet(false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        Text("APLICAR FILTROS", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ClientCard(
    client: UsuarioResumo,
    onEdit: () -> Unit,
    onCalibrate: () -> Unit,
    onTerminal: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(client.nome ?: "N/A", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("@${client.login}", color = TextMuted, fontSize = 12.sp)
                }
                Surface(color = PrimaryColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text((client.perfil_risco ?: "Moderado").uppercase(), color = PrimaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                }
            }
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("R$ ${String.format(Locale.GERMANY, "%,.2f", client.saldo_disponivel ?: 0.0)}", color = CompraColor, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onTerminal) { Icon(Icons.Default.Terminal, null, tint = PrimaryColor) }
                    IconButton(onClick = onCalibrate) { Icon(Icons.Default.Settings, null, tint = TextPrimary) }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = TextPrimary) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = VendaColor) }
                }
            }
        }
    }
}