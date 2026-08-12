package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.PontoCurvaCapital
import quantadvisor.com.br.data.model.ReplayDecisao
import quantadvisor.com.br.data.model.ResumoEstrategia
import quantadvisor.com.br.ui.theme.*
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionalTearsheetScreen(
    onTerminalClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    onCrmClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: TearsheetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(24.dp).padding(start = 8.dp)
                        )
                        Text(
                            "QuantAdvisor",
                            color = PrimaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = "Tendência",
                            tint = TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceContainerHighest) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Terminal") },
                    selected = false,
                    onClick = onTerminalClick
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                    label = { Text("Portfólio") },
                    selected = true,
                    onClick = onPortfolioClick
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    label = { Text("CRM") },
                    selected = false,
                    onClick = onCrmClick
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Perfil") },
                    selected = false,
                    onClick = onProfileClick
                )
            }
        }
    ) { innerPadding ->
        when (uiState) {
            is TearsheetUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            }
            is TearsheetUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Erro: ${(uiState as TearsheetUiState.Error).message}", color = VendaColor)
                }
            }
            is TearsheetUiState.Success -> {
                val state = uiState as TearsheetUiState.Success
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(CompraColor, CircleShape))
                            Text("LIVE STREAM", color = CompraColor, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Text("Tearsheet Institucional", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }

                    // KPI Grid
                    val res = state.resumo
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TearsheetKpiCard(
                            modifier = Modifier.weight(1f),
                            label = "Capital Atual (Net)",
                            value = "$ ${String.format(Locale.GERMANY, "%,.2f", res?.capital_atual_net ?: 0.0)}",
                            accentColor = PrimaryContainer
                        )
                        TearsheetKpiCard(
                            modifier = Modifier.weight(1f),
                            label = "Lucro Líquido (YTD)",
                            value = "+$ ${String.format(Locale.GERMANY, "%,.2f", res?.lucro_liquido_net ?: 0.0)}",
                            accentColor = CompraColor,
                            subValue = "+${String.format(Locale.GERMANY, "%.1f", res?.win_rate_net ?: 0.0)}%",
                            icon = Icons.AutoMirrored.Filled.TrendingUp
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TearsheetKpiCard(
                            modifier = Modifier.weight(1f),
                            label = "Max Drawdown",
                            value = "-${String.format(Locale.GERMANY, "%.2f", res?.max_drawdown ?: 0.0)}%",
                            accentColor = VendaColor,
                            subValue = "High water mark",
                            icon = Icons.Default.Warning
                        )
                        TearsheetKpiCard(
                            modifier = Modifier.weight(1f),
                            label = "Total de Operações",
                            value = "${res?.total_operacoes ?: 0}",
                            accentColor = SurfaceVariant,
                            subValue = "últimos 30d"
                        )
                    }

                    // Composite Chart Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("Evolução Patrimonial vs Volatilidade", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Surface(color = SurfaceContainerLow, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, OutlineVariant)) {
                                    Text("YTD", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            // Simulated Chart Area
                            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                                // Volatility Bars
                                Row(Modifier.fillMaxSize(), Arrangement.SpaceBetween, Alignment.Bottom) {
                                    val items = state.curva.ifEmpty { List(12) { null } }
                                    items.forEachIndexed { i, pt ->
                                        val h = if(pt != null) (pt.volatilidade_mercado / 100).toFloat().coerceIn(0.1f, 0.9f) else 0.2f
                                        val barColor = when {
                                            pt == null -> TertiaryContainer.copy(alpha = 0.3f)
                                            pt.volatilidade_mercado > 40 -> VendaColor.copy(alpha = 0.3f)
                                            pt.volatilidade_mercado < 20 -> CompraColor.copy(alpha = 0.3f)
                                            else -> TertiaryContainer.copy(alpha = 0.3f)
                                        }
                                        Box(Modifier.weight(1f).fillMaxHeight(h).padding(horizontal = 2.dp).background(barColor))
                                    }
                                }

                                // Equity Line
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (state.curva.isNotEmpty()) {
                                        val path = Path()
                                        val maxVal = state.curva.maxOf { it.patrimonio_net }
                                        val minVal = state.curva.minOf { it.patrimonio_net }
                                        val range = (maxVal - minVal).coerceAtLeast(1.0)
                                        
                                        val xStep = size.width / (state.curva.size - 1)
                                        
                                        state.curva.forEachIndexed { i, p ->
                                            val y = ((p.patrimonio_net - minVal) / range).toFloat()
                                            val yCoord = (1f - y) * size.height
                                            if (i == 0) path.moveTo(0f, yCoord)
                                            else path.lineTo(i * xStep, yCoord)
                                        }

                                        drawPath(
                                            path = path,
                                            color = PrimaryColor,
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }
                            }
                            
                            // Months Row
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween) {
                                listOf("Jan", "Mar", "Mai", "Jul", "Set", "Hoje").forEach {
                                    Text(it, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Decision Replay Table
                    Column {
                        Text("📜 REPLAY DE DECISÃO", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                            border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column {
                                val replayItems = state.replay

                                if (replayItems.isEmpty()) {
                                    Text("Nenhuma operação registrada na auditoria da IA.", color = TextMuted, modifier = Modifier.padding(16.dp))
                                } else {
                                    replayItems.forEach { item ->
                                        ReplayRow(item)
                                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ReplayRow(item: ReplayDecisao) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.timestamp, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(item.ativo, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        
        Box(Modifier.weight(0.8f)) {
            val c = if(item.acao_executada == "COMPRA") CompraColor else VendaColor
            Surface(color = c.copy(alpha = 0.1f), border = BorderStroke(1.dp, c.copy(alpha = 0.2f)), shape = RoundedCornerShape(2.dp)) {
                Text(item.acao_executada, color = c, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
            Text("Z: ${item.z_score} | K: ${item.fator_kelly_alocado}%", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(item.regime_mercado, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TearsheetKpiCard(
    modifier: Modifier,
    label: String,
    value: String,
    accentColor: Color,
    subValue: String? = null,
    icon: ImageVector? = null
) {
    Card(
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f))
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor))
            Column(Modifier.padding(12.dp).padding(start = 6.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(label.uppercase(), color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    icon?.let { Icon(it, null, Modifier.size(14.dp), tint = accentColor) }
                }
                Spacer(Modifier.height(8.dp))
                Text(value, color = if(accentColor == CompraColor || accentColor == VendaColor) accentColor else TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                subValue?.let {
                    Spacer(Modifier.weight(1f))
                    Text(it, color = if(accentColor == CompraColor) CompraColor else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}