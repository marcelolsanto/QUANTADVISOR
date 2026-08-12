package quantadvisor.com.br.ui.screens

import quantadvisor.com.br.di.RetrofitClient
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import quantadvisor.com.br.ui.screens.crm.PerfilViewModel

enum class Jurisdicao { BR, US }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilGestorScreen(
    onBackClick: () -> Unit = {},
    onEditProfileClick: (UsuarioResumo) -> Unit = {},
    onCalibrateRobotClick: (UsuarioResumo) -> Unit = {},
    onLogoutSuccess: () -> Unit = {},
    viewModel: PerfilViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedJurisdicao by remember { mutableStateOf(Jurisdicao.BR) }

    LaunchedEffect(uiState.isLogoutSuccess) {
        if (uiState.isLogoutSuccess) onLogoutSuccess()
    }

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "QuantAdvisor",
                        color = PrimaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = "TendÃªncia",
                            tint = TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // SeÃ§Ã£o: Identidade do UsuÃ¡rio
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHighest)
                                .border(2.dp, PrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = PrimaryColor,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Indicador Status Ativo
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(CompraColor)
                                .border(3.dp, BgBackground, CircleShape)
                        )
                    }

                    Text(
                        text = uiState.user?.nome ?: "Carregando...",
                        color = TextPrimary,
                        fontSize = 26.sp, // Aumentado para destaque
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PrimaryColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, PrimaryColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = (uiState.user?.perfil_risco ?: "GESTOR").uppercase(),
                                color = PrimaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "ID: ${uiState.user?.id ?: "----"}-QA",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (uiState.user != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Card de Saldo com LÃ³gica de Cor
                            val saldo = uiState.user!!.saldo_disponivel ?: 0.0
                            val saldoColor = when {
                                saldo > 0 -> CompraColor
                                saldo < 0 -> VendaColor
                                else -> TextPrimary
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                                border = BorderStroke(1.dp, saldoColor.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("SALDO", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "R$ ${String.format(Locale.GERMANY, "%,.2f", saldo)}",
                                        color = saldoColor,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Card de Lucro com LÃ³gica de Cor
                            val lucro = uiState.user!!.lucro_acumulado ?: 0.0
                            val lucroColor = when {
                                lucro > 0 -> CompraColor
                                lucro < 0 -> VendaColor
                                else -> TextPrimary
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                                border = BorderStroke(1.dp, lucroColor.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("LUCRO ACUM.", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "R$ ${String.format(Locale.GERMANY, "%,.2f", lucro)}",
                                        color = lucroColor,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SeÃ§Ã£o: JurisdiÃ§Ã£o de OperaÃ§Ã£o
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "JURISDIÃ‡ÃƒO DE OPERAÃ‡ÃƒO",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceContainer, shape = RoundedCornerShape(8.dp))
                            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        JurisdicaoTabButton(
                            text = "BR B3",
                            icon = Icons.Default.CurrencyExchange,
                            isSelected = selectedJurisdicao == Jurisdicao.BR,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedJurisdicao = Jurisdicao.BR }
                        )

                        JurisdicaoTabButton(
                            text = "US WALL ST",
                            icon = Icons.Default.AccountBalance,
                            isSelected = selectedJurisdicao == Jurisdicao.US,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedJurisdicao = Jurisdicao.US }
                        )
                    }
                }
            }

            // SeÃ§Ã£o: ConfiguraÃ§Ãµes / Itens do Menu
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CONFIGURAÃ‡Ã•ES",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )

                    ConfigMenuItem(
                        icon = Icons.Default.Person,
                        title = "Editar Perfil",
                        subtitle = "Dados pessoais e credenciais",
                        onClick = { uiState.user?.let { onEditProfileClick(it) } }
                    )

                    ConfigMenuItem(
                        icon = Icons.Default.Settings,
                        title = "Calibrar Meu RobÃ´",
                        subtitle = "Ajuste de sensibilidade e risco",
                        onClick = { uiState.user?.let { onCalibrateRobotClick(it) } }
                    )
                }
            }

            // BotÃ£o Sair (Logout)
            item {
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VendaColor.copy(alpha = 0.1f),
                        contentColor = VendaColor
                    ),
                    border = BorderStroke(1.dp, VendaColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sair",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sair",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // RodapÃ© / VersÃ£o
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = "QUANTADVISOR TERMINAL",
                        color = TextMuted.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "v4.2.0-STABLE | SECURED BY QUANT-SHIELD",
                        color = TextMuted.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun JurisdicaoTabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) PrimaryContainer else Color.Transparent
    val contentColor = if (isSelected) OnPrimaryContainer else TextMuted

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConfigMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(OnSecondary, shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = PrimaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}