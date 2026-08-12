package quantadvisor.com.br.ui.screens

import quantadvisor.com.br.di.RetrofitClient
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.ui.theme.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibragemScreen(
    user: UsuarioResumo?,
    onBackClick: () -> Unit,
    onCalibrationSuccess: () -> Unit,
    viewModel: CalibragemViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(user) {
        viewModel.setUser(user)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onCalibrationSuccess()
    }

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Calibragem", color = PrimaryColor, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Voltar", tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceContainerHighest,
                border = BorderStroke(1.dp, OutlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OutlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("CANCELAR")
                    }
                    Button(
                        onClick = { viewModel.salvar() },
                        modifier = Modifier.weight(2f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CompraColor)
                    ) {
                        if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else {
                            Icon(Icons.Default.Rocket, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("INICIAR CALIBRAGEM", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header de SeleÃ§Ã£o
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer, RoundedCornerShape(12.dp))
                    .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ajustando robÃ´ de:", color = TextMuted, fontSize = 14.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SurfaceContainerHighest, RoundedCornerShape(4.dp))
                                .border(1.dp, OutlineVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(uiState.user?.nome ?: "Meu Perfil", color = TextPrimary, fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    HorizontalDivider(color = OutlineVariant)

                    // MODO PILOTO AUTOMÃTICO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("PILOTO AUTOMÃTICO (IA)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Permite que o motor execute ordens via B3", color = TextMuted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = uiState.isPilotActive,
                            onCheckedChange = { viewModel.togglePiloto(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CompraColor,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = OutlineVariant
                            )
                        )
                    }
                }
            }

            // Banner de Alerta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer, RoundedCornerShape(12.dp))
                    .border(1.dp, AlertaColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AlertaColor, modifier = Modifier.size(20.dp))
                    Text(
                        "Aplicando Setup de TolerÃ¢ncia a Risco: ${uiState.user?.perfil_risco?.uppercase() ?: "AGRESSIVO"}",
                        color = AlertaColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SeÃ§Ã£o de Dimensionamento
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer, RoundedCornerShape(12.dp))
                    .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(20.dp))
                    Text("Dimensionamento de PosiÃ§Ã£o", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                HorizontalDivider(color = OutlineVariant)

                // Setting 1
                SliderSetting(
                    title = "Agressividade (FraÃ§Ã£o de Kelly)",
                    value = uiState.kellyFraction,
                    onValueChange = { viewModel.onKellyChange(it) },
                    valueText = "${uiState.kellyFraction.toInt()}%",
                    color = PrimaryColor,
                    description = "Determina o quanto o robÃ´ confia na IA. Valores pequenos (5%) geram um giro rÃ¡pido sem abalar o patrimÃ´nio principal (Scalp).",
                    minLabel = "Conservador (5%)",
                    maxLabel = "Agressivo (50%)"
                )

                // Setting 2
                SliderSetting(
                    title = "ConcentraÃ§Ã£o MÃ¡xima por Ativo",
                    value = uiState.maxConcentration,
                    onValueChange = { viewModel.onMaxConcentrationChange(it) },
                    valueText = "${uiState.maxConcentration.toInt()}%",
                    color = AlertaColor,
                    description = "Teto de exposiÃ§Ã£o direcional para evitar risco de ruÃ­na caso uma empresa quebre.",
                    minLabel = "DiluÃ­do (5%)",
                    maxLabel = "Concentrado (30%)"
                )

                // Setting 3
                SliderSetting(
                    title = "Take Profit Fixo (Rebalanceamento)",
                    value = uiState.takeProfit,
                    onValueChange = { viewModel.onTakeProfitChange(it) },
                    valueText = "+${uiState.takeProfit.toInt()}%",
                    color = CompraColor,
                    description = "ForÃ§a a venda da posiÃ§Ã£o para realizar o lucro percentual e voltar para a seguranÃ§a de caixa.",
                    minLabel = "Curto (+5%)",
                    maxLabel = "Longo (+25%)"
                )
            }

            // Trava de SeguranÃ§a
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181111), RoundedCornerShape(12.dp))
                    .border(1.dp, VendaColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = VendaColor, modifier = Modifier.size(20.dp))
                        Text("Trava de SeguranÃ§a (Max Drawdown)", color = VendaColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Interrompe as operaÃ§Ãµes do robÃ´ automaticamente se a perda mÃ¡xima da carteira atingir este limite no dia.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, VendaColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Stop Loss Global", color = TextPrimary, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${String.format(Locale.US, "%.2f", uiState.stopLoss)}%", color = VendaColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(SurfaceContainerHighest, RoundedCornerShape(4.dp))
                                    .clickable { /* Mudar stop loss */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun SliderSetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String,
    color: Color,
    description: String,
    minLabel: String,
    maxLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(valueText, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Text(description, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = SliderBg
            )
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minLabel, color = TextMuted, fontSize = 10.sp)
            Text(maxLabel, color = TextMuted, fontSize = 10.sp)
        }
    }
}