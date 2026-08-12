package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.AssetAnalysis
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetAnalysisScreen(
    tickerArg: String? = null,
    onBack: () -> Unit,
    viewModel: AssetAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tickerArg) {
        if (!tickerArg.isNullOrBlank()) {
            viewModel.onSearchQueryChanged(tickerArg)
            viewModel.analyzeAsset()
        }
    }

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Raio-X Institucional", fontWeight = FontWeight.Bold, color = PrimaryColor, fontSize = 16.sp)
                        if (uiState.asset != null) {
                            Text(uiState.asset!!.nome_empresa, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
                    }
                },
                actions = {
                    if (uiState.asset != null) {
                        IconButton(onClick = { /* Refresh logic */ }) {
                            Icon(Icons.Default.Refresh, null, tint = PrimaryColor)
                        }
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
        ) {
            // BARRA DE BUSCA
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    label = { Text("Ticker (ex: PETR4)", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.analyzeAsset() }) {
                            Icon(Icons.Default.Search, "Analisar", tint = PrimaryColor)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceContainer,
                        unfocusedContainerColor = SurfaceContainer,
                        focusedBorderColor = PrimaryColor
                    ),
                    singleLine = true
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            } else if (uiState.error != null) {
                Box(Modifier.padding(16.dp)) {
                    Surface(color = VendaColor.copy(alpha = 0.1f), border = BorderStroke(1.dp, VendaColor), shape = RoundedCornerShape(8.dp)) {
                        Text("Erro: ${uiState.error}", color = VendaColor, modifier = Modifier.padding(16.dp))
                    }
                }
            } else if (uiState.asset != null) {
                AssetAnalysisContent(uiState.asset!!)
            } else {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("Digite um ticker para auditoria profunda.", color = TextMuted, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun AssetAnalysisContent(asset: AssetAnalysis) {
    val cardBg = Color(0xFF0A101D)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- HEADER SECTION (MATCH IMAGE 1) ---
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(asset.ticker, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(asset.nome_empresa, color = Color(0xFF3B82F6), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text("Raio-X Quantitativo & Fundamentalista", color = TextMuted, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))

            // CHIPS INSTITUCIONAIS
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(Icons.Default.MedicalServices, asset.setor, Color(0xFF3B82F6))
                InfoChip(Icons.Default.Business, asset.industria, Color.Gray)
                InfoChip(Icons.Default.Groups, "${asset.colaboradores} Colaboradores", Color.Gray)
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = VendaColor, modifier = Modifier.size(14.dp))
                Text(buildAnnotatedString {
                    append("Residência Fiscal (Sede): ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(asset.sede) }
                }, color = TextMuted, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Language, null, tint = InfoColor, modifier = Modifier.size(14.dp))
                Text(buildAnnotatedString {
                    append("Site Oficial: ")
                    withStyle(SpanStyle(color = InfoColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(asset.website) }
                }, color = TextMuted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))

            // DESCRIÇÃO (EXPANDABLE)
            var expanded by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer, RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubble, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("História e Operação:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = asset.resumo,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }

        // --- CHART SECTION (MATCH IMAGE 2) ---
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF030712), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartTimeChip("1M")
                ChartTimeChip("3M", selected = true)
                ChartTimeChip("6M")
                ChartTimeChip("1A")
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Simple Drawing of Line Chart (Placeholder for ycharts complexity in single edit)
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                if (asset.historico_precos.isNotEmpty()) {
                    SimpleLineChart(asset.historico_precos)
                } else {
                    Text("Dados históricos indisponíveis", Modifier.align(Alignment.Center), color = TextMuted)
                }
            }
        }

        // --- DECISION & HEALTH CARDS (MATCH IMAGE 3) ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Decisão PPO
            Card(
                modifier = Modifier.weight(1f).height(140.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, InfoColor.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, null, tint = InfoColor, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Agente PPO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Button(
                            onClick = { /* Open Trade */ },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InfoColor)
                        ) {
                            Text("Negociar", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Column {
                        Text("Veredicto Final:", color = TextMuted, fontSize = 9.sp)
                        val color = if(asset.ai_decision.contains("COMPRA")) CompraColor else if(asset.ai_decision.contains("VENDA")) VendaColor else Color(0xFFFF8C00)
                        Text(asset.ai_decision, color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Column {
                            Text("Z-Score", color = TextMuted, fontSize = 8.sp)
                            Text(String.format("%.2f", asset.z_score), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("VaR 99%", color = TextMuted, fontSize = 8.sp)
                            Text("${String.format("%.1f", asset.volatilidade)}%", color = CompraColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Saúde Estrutural
            Card(
                modifier = Modifier.weight(1f).height(140.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, CompraColor.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, null, tint = CompraColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Saúde (Yahoo)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    
                    MetricRow("Dívida / EBITDA", "${String.format("%.2f", asset.divida_ebitda)}x", CompraColor)
                    MetricRow("Margem Líquida", "${String.format("%.1f", asset.margem_liquida)}%", Color.White)
                    MetricRow("Preço (MtM)", "R$ ${String.format("%.2f", asset.preco_atual)}", InfoColor)
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
fun ChartTimeChip(text: String, selected: Boolean = false) {
    Surface(
        color = if(selected) InfoColor else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = if(!selected) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
    ) {
        Text(
            text = text, 
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = if(selected) Color.White else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SimpleLineChart(prices: List<Double>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val max = prices.maxOrNull() ?: 1.0
        val min = prices.minOrNull() ?: 0.0
        val range = max - min
        val width = size.width
        val height = size.height
        
        val path = Path()
        prices.forEachIndexed { index, price ->
            val x = index * (width / (prices.size - 1))
            val y = height - ((price - min) / range * height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // EMA placeholder (green line)
        val pathEma = Path()
        prices.forEachIndexed { index, price ->
            val x = index * (width / (prices.size - 1))
            val offset = (Math.sin(index.toDouble() / 5.0) * 10).toFloat()
            val y = height - ((price - min) / range * height).toFloat() + offset
            if (index == 0) pathEma.moveTo(x, y) else pathEma.lineTo(x, y)
        }
        drawPath(path = pathEma, color = CompraColor, style = Stroke(width = 1.dp.toPx()))
    }
}
