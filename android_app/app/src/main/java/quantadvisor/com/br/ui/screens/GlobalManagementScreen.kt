package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.PontoHistorico
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalManagementScreen(
    onTerminalClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    onCrmClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onComplianceClick: () -> Unit = {},
    onInstitutionalClick: () -> Unit = {},
    onAssetAnalysisClick: () -> Unit = {},
    onBatchOperationsClick: () -> Unit = {},
    viewModel: GlobalViewModel = hiltViewModel()
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
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "GestÃ£o Global",
                            color = PrimaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBatchOperationsClick) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Carrinho Noturno",
                            tint = PrimaryColor
                        )
                    }
                    IconButton(onClick = onAssetAnalysisClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Raio-X",
                            tint = PrimaryColor
                        )
                    }
                    IconButton(onClick = onComplianceClick) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = "Compliance",
                            tint = PrimaryColor
                        )
                    }
                    IconButton(onClick = onInstitutionalClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = "TendÃªncia",
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
                    label = { Text("PortfÃ³lio") },
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
            is GlobalUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            }
            is GlobalUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Erro: ${(uiState as GlobalUiState.Error).message}", color = VendaColor)
                }
            }
            is GlobalUiState.Success -> {
                val state = uiState as GlobalUiState.Success
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // AUM Summary Grid
                    AumSummarySection(state)

                    // Market Regime Indicator
                    MarketRegimeSection(state.macro?.regime_atual ?: "ANALISANDO")

                    // Evolution Chart
                    EvolutionChartSection(state.history)

                    // Top Clients List
                    TopClientsSection()

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun AumSummarySection(state: GlobalUiState.Success) {
    val dash = state.dashboard
    val macro = state.macro
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Total AUM Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.width(4.dp).height(100.dp).align(Alignment.CenterStart).background(PrimaryColor))
                Column(Modifier.padding(16.dp).padding(start = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("TOTAL AUM (BRL)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Icon(Icons.Default.Public, null, Modifier.size(16.dp), tint = PrimaryColor)
                    }
                    Text("R$ ${String.format(Locale.GERMANY, "%,.0f", dash?.patrimonio_total ?: 0.0)}", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.ArrowUpward, null, Modifier.size(14.dp), tint = CompraColor)
                        Text("+2.4% (YTD)", color = CompraColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(4.dp).height(80.dp).align(Alignment.CenterStart).background(CompraColor))
                    Column(Modifier.padding(12.dp).padding(start = 6.dp)) {
                        Text("DÃ“LAR (USD)", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("R$ ${String.format(Locale.GERMANY, "%.2f", macro?.cotacao_dolar_ativa ?: 0.0)}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                        Text("Comercial", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(4.dp).height(80.dp).align(Alignment.CenterStart).background(AlertaColor))
                    Column(Modifier.padding(12.dp).padding(start = 6.dp)) {
                        Text("REGIME ATUAL", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(macro?.regime_atual ?: "ANALISANDO", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(4.dp).height(80.dp).align(Alignment.CenterStart).background(PrimaryColor))
                    Column(Modifier.padding(12.dp).padding(start = 6.dp)) {
                        Text("CAIXA LIVRE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("R$ ${String.format(Locale.GERMANY, "%,.1f", dash?.caixa_livre ?: 0.0)}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                        Text("DisponÃ­vel", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(4.dp).height(80.dp).align(Alignment.CenterStart).background(CompraColor))
                    Column(Modifier.padding(12.dp).padding(start = 6.dp)) {
                        Text("ALOCADO", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("R$ ${String.format(Locale.GERMANY, "%,.1f", dash?.custo_aquisicao ?: 0.0)}", color = CompraColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                        Text("MtM", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MarketRegimeSection(regime: String) {
    val isBear = regime.contains("BEAR")
    val color = if (isBear) VendaColor else CompraColor
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("MARKET REGIME HMM", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(regime.uppercase(), color = color, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if(isBear) "ðŸ»" else "ðŸ‚", fontSize = 18.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).background(if(!isBear) CompraColor else OutlineVariant, CircleShape))
                Box(Modifier.size(8.dp).background(if(isBear) VendaColor else OutlineVariant, CircleShape))
            }
        }
    }
}

@Composable
fun EvolutionChartSection(history: List<PontoHistorico>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("EVOLUÃ‡ÃƒO AUM", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1M", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { })
                    Surface(color = SurfaceContainerHighest, shape = RoundedCornerShape(4.dp)) {
                        Text("YTD", color = PrimaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (history.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Iniciando varredura quantitativa...", color = TextMuted)
                }
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val path = Path()
                    val values = history.map { it.patrimonio.toFloat() }
                    val yMin = values.minOrNull() ?: 0f
                    val yMax = values.maxOrNull() ?: 1f
                    val yRange = (yMax - yMin).coerceAtLeast(1f)
                    
                    val xStep = size.width / (values.size - 1).coerceAtLeast(1)
                    
                    fun normalizeY(v: Float): Float = size.height - ((v - yMin) / yRange) * size.height
                    
                    path.moveTo(0f, normalizeY(values[0]))
                    values.forEachIndexed { i, v ->
                        path.lineTo(i * xStep, normalizeY(v))
                    }

                    val areaPath = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(PrimaryColor.copy(alpha = 0.3f), Color.Transparent)
                        )
                    )

                    drawPath(
                        path = path,
                        color = PrimaryColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    history.firstOrNull()?.let { Text(it.data, color = TextMuted, fontSize = 9.sp) }
                    history.lastOrNull()?.let { Text(it.data, color = TextMuted, fontSize = 9.sp) }
                }
            }
        }
    }
}

@Composable
fun TopClientsSection() {
    Column {
        Text("TOP CLIENTES ATIVOS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClientPerformanceRow("Fundo Alpha Multimercado", "Aggressive", "R$ 450.2M", "+1.2%", CompraColor)
            ClientPerformanceRow("Beta Institucional FIA", "Moderate", "R$ 380.5M", "+4.5%", CompraColor)
            ClientPerformanceRow("Gama Global Macro", "Global", "R$ 210.0M", "+0.8%", CompraColor)
        }
    }
}

@Composable
fun ClientPerformanceRow(name: String, risk: String, amount: String, perf: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(3.dp).height(24.dp).background(color, RoundedCornerShape(2.dp)))
                Column {
                    Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(risk, color = TextMuted, fontSize = 11.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(perf, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}