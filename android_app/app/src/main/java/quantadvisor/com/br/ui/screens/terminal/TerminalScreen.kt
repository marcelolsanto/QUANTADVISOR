package quantadvisor.com.br.ui.screens.terminal

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.R
import quantadvisor.com.br.data.model.*
import quantadvisor.com.br.ui.components.MarketToggle
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@Composable
fun LiveTerminalScreen(
    userIdFromNav: Int,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
    onNewsClick: () -> Unit,
    onGlobalClick: () -> Unit,
    onBacktestClick: () -> Unit,
    onMonteCarloClick: () -> Unit,
    onAssetAnalysisClick: (String) -> Unit,
    onTradeClick: (String, Double) -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMarket by viewModel.marketSession.currentMarket.collectAsState()
    var userMenuExpanded by remember { mutableStateOf(false) }

    // 🛡️ BLOQUEAR SAÍDA ACIDENTAL NO TERMINAL
    BackHandler {
        onBackClick()
    }

    val bgDark = Color(0xFF050A15)
    val cardBg = Color(0xFF0A101D)
    val textMain = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF94A3B8)
    val colorCompra = Color(0xFF10B981)
    val colorVenda = Color(0xFFEF4444)
    val colorInfo = Color(0xFF3B82F6)

    val topCardsHeight = 160.dp

    // Estados de Expansão
    var isLiveFlowExpanded by remember { mutableStateOf(true) }
    var isCustodyExpanded by remember { mutableStateOf(true) }
    var isAuditoriaExpanded by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var isCartExpanded by remember { mutableStateOf(false) }
    var isNewsExpanded by remember { mutableStateOf(true) }

    // Estados de Filtro (Pesquisa)
    var liveFilter by remember { mutableStateOf("") }
    var custodyFilter by remember { mutableStateOf("") }
    var auditoriaFilter by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf("") }
    var cartFilter by remember { mutableStateOf("") }

    // Estados de Filtros Avançados (Chips)
    var liveDecisionFilter by remember { mutableStateOf("TODOS") }
    var custodyPnLFilter by remember { mutableStateOf("TODOS") }
    var auditoriaDecisionFilter by remember { mutableStateOf("TODOS") }
    var historyTypeFilter by remember { mutableStateOf("TODOS") }

    // Estados de Ordenação
    var liveSortKey by remember { mutableStateOf("hora") }
    var liveSortAsc by remember { mutableStateOf(false) }
    var custodySortKey by remember { mutableStateOf("ticker") }
    var custodySortAsc by remember { mutableStateOf(true) }
    var auditoriaSortKey by remember { mutableStateOf("ativo") }
    var auditoriaSortAsc by remember { mutableStateOf(true) }

    LaunchedEffect(userIdFromNav) {
        viewModel.initTerminal(userIdFromNav)
    }

    Scaffold(
        containerColor = bgDark,
        topBar = {
            Surface(color = bgDark, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = textMain)
                        }
                        Column(modifier = Modifier.clickable { userMenuExpanded = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(uiState.selectedUser?.nome ?: "Dono...", color = textMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Icon(Icons.Default.ArrowDropDown, null, tint = textMuted)
                            }
                            Surface(color = colorInfo.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(uiState.selectedUser?.perfil_risco?.uppercase() ?: "---", color = colorInfo, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            DropdownMenu(expanded = userMenuExpanded, onDismissRequest = { userMenuExpanded = false }) {
                                uiState.allUsers.forEach { user ->
                                    DropdownMenuItem(text = { Text(user.nome ?: "N/A") }, onClick = { viewModel.switchUser(user); userMenuExpanded = false })
                                }
                            }
                        }
                    }
                    MarketToggle(currentMarket, onMarketChange = { viewModel.marketSession.setMarket(it) })
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. CARDS FIXOS DO TOPO (Patrimônio)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(topCardsHeight),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    val symbol = if (currentMarket == "USD") "$" else "R$"
                    val locale = if (currentMarket == "USD") Locale.US else Locale.GERMANY
                    val pnlEx = if(currentMarket == "USD") (uiState.totalPl / 5.5) else uiState.totalPl
                    val patrimonioTotal = if(currentMarket == "USD") (uiState.dashboard?.patrimonio_total ?: 0.0)/5.5 else (uiState.dashboard?.patrimonio_total ?: 0.0)
                    val saldoCaixa = if(currentMarket == "USD") (uiState.selectedUser?.saldo_usd ?: 0.0) else (uiState.selectedUser?.saldo_disponivel ?: 0.0)

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Column {
                                Text("Patrimônio Líquido ($currentMarket)", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "$symbol ${String.format(locale, "%,.2f", patrimonioTotal)}",
                                    color = colorInfo, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Oportunidades Claras", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = uiState.buyCount.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, null, tint = Color(0xFFFF8C00), modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sinais de Compra Forte", color = colorCompra, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("SALDO CAIXA", color = textMuted, fontSize = 9.sp)
                                Text("$symbol ${String.format(locale, "%,.2f", saldoCaixa)}", color = colorCompra, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LUCRO ACUMULADO", color = textMuted, fontSize = 9.sp)
                                Text("${if(pnlEx >= 0) "+" else ""}${String.format(locale, "%,.2f", pnlEx)}", color = if(pnlEx >= 0) colorCompra else colorVenda, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. ÚLTIMO GATILHO DETECTADO (RADAR IA)
            item {
                val alvo = uiState.ultimoAlvo
                val sColor = if(alvo?.sinalExibicao?.contains("COMPRA") == true) colorCompra else if(alvo?.sinalExibicao?.contains("VENDA") == true) colorVenda else colorInfo
                
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("🎯", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("ÚLTIMO GATILHO DETECTADO", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, sColor.copy(alpha = 0.5f))
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            // O RETORNO DO TOURO! (Ao fundo, centralizado)
                            DynamicBullLogo(
                                signalColor = sColor,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .alpha(0.6f) // Deixando levemente transparente para não ofuscar o texto
                            )

                            Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                if (alvo != null) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Column {
                                            Text(
                                                text = alvo.ativo ?: "---", 
                                                color = textMain, fontSize = 28.sp, fontWeight = FontWeight.Black,
                                                modifier = Modifier.clickable { onAssetAnalysisClick(alvo.ativo ?: "") }
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            val symbol = if (currentMarket == "USD") "$" else "R$"
                                            Text(buildAnnotatedString {
                                                withStyle(style = SpanStyle(color = textMuted)) { append("Preço: ") }
                                                withStyle(style = SpanStyle(color = colorInfo, fontWeight = FontWeight.Bold)) {
                                                    append("$symbol ${String.format(Locale.US, "%.2f", alvo.precoAtual ?: 0.0)}")
                                                }
                                            }, fontSize = 14.sp)
                                            Spacer(Modifier.height(4.dp))
                                            val vwapColor = if((alvo.distVwap ?: 0.0) >= 0) colorCompra else colorVenda
                                            Text(buildAnnotatedString {
                                                withStyle(style = SpanStyle(color = textMuted)) { append("Δ VWAP: ") }
                                                withStyle(style = SpanStyle(color = vwapColor, fontWeight = FontWeight.Bold)) {
                                                    val signal = if((alvo.distVwap ?: 0.0) >= 0) "+" else ""
                                                    append("$signal${String.format(Locale.US, "%.2f", alvo.distVwap ?: 0.0)}%")
                                                }
                                            }, fontSize = 14.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Surface(color = sColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                                Text(alvo.sinalExibicao, color = sColor, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            Text(buildAnnotatedString {
                                                withStyle(style = SpanStyle(color = textMuted)) { append("Z-Score: ") }
                                                withStyle(style = SpanStyle(color = textMain, fontWeight = FontWeight.Bold)) {
                                                    append(String.format(Locale.US, "%.2f", alvo.zScore ?: 0.0))
                                                }
                                            }, fontSize = 14.sp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(buildAnnotatedString {
                                                withStyle(style = SpanStyle(color = textMuted)) { append("Vol_Z: ") }
                                                withStyle(style = SpanStyle(color = textMain, fontWeight = FontWeight.Bold)) {
                                                    append(String.format(Locale.US, "%.2f", alvo.volZScore ?: 0.0))
                                                }
                                            }, fontSize = 14.sp)
                                            Spacer(Modifier.height(12.dp))
                                            Text("Visto às ${alvo.hora}", color = textMuted, fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Aguardando varredura de mercado...",
                                            color = textMuted,
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. TERMÔMETRO DO LOTE ATUAL
            item {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("📊", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("TERMÔMETRO DO LOTE ATUAL", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Column {
                                    Text(String.format(Locale.US, "%.2f", uiState.avgZ), color = textMain, fontWeight = FontWeight.Black, fontSize = 28.sp, fontFamily = FontFamily.Monospace)
                                    Text("Z-Score Médio", color = textMuted, fontSize = 11.sp)
                                }
                                Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.1f)))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${String.format(Locale.US, "%.1f", uiState.avgVar)}%", color = colorVenda, fontWeight = FontWeight.Black, fontSize = 28.sp, fontFamily = FontFamily.Monospace)
                                    Text("VaR Médio", color = textMuted, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Column {
                                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.3f))) {
                                    Box(Modifier.fillMaxHeight().weight(uiState.buyPct.coerceAtLeast(0.01f)).background(colorCompra))
                                    Box(Modifier.fillMaxHeight().weight(uiState.neutralPct.coerceAtLeast(0.01f)).background(colorInfo))
                                    Box(Modifier.fillMaxHeight().weight(uiState.sellPct.coerceAtLeast(0.01f)).background(colorVenda))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("${uiState.buyCount} C", color = colorCompra, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    Text("${uiState.neutralCount} N", color = colorInfo, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    Text("${uiState.sellCount} V", color = colorVenda, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }

            // --- SEÇÕES EXPANSÍVEIS ---

            // 1. QUANT ENGINE LIVE FLOW
            item {
                val statusIcon = if (uiState.isConnected) "🟢 ONLINE" else "🔴 OFFLINE"
                ExpandableSection(
                    title = "QUANT ENGINE LIVE FLOW [$statusIcon]",
                    count = uiState.logs.size,
                    isExpanded = isLiveFlowExpanded,
                    onToggle = { isLiveFlowExpanded = !isLiveFlowExpanded },
                    filterQuery = liveFilter,
                    onFilterChange = { liveFilter = it },
                    filterContent = {
                        FilterRow(
                            selected = liveDecisionFilter,
                            options = listOf("TODOS", "COMPRA FORTE", "ALERTA DE VENDA", "NEUTRO"),
                            onSelect = { liveDecisionFilter = it }
                        )
                    },
                    headerContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeaderCell("HORA", 55.dp, isSorted = liveSortKey == "hora", asc = liveSortAsc, onClick = { if(liveSortKey=="hora") liveSortAsc=!liveSortAsc else {liveSortKey="hora"; liveSortAsc=false} })
                            HeaderCell("ATIVO", 60.dp, isSorted = liveSortKey == "ativo", asc = liveSortAsc, onClick = { if(liveSortKey=="ativo") liveSortAsc=!liveSortAsc else {liveSortKey="ativo"; liveSortAsc=true} })
                            HeaderCell("MtM", 80.dp)
                            HeaderCell("Z", 50.dp, isSorted = liveSortKey == "z", asc = liveSortAsc, onClick = { if(liveSortKey=="z") liveSortAsc=!liveSortAsc else {liveSortKey="z"; liveSortAsc=true} })
                            HeaderCell("VaR", 50.dp)
                            HeaderCell("VWAP", 70.dp)
                            HeaderCell("VOL", 50.dp)
                            HeaderCell("IA", 120.dp)
                        }
                    }
                ) {
                    val logs = uiState.logs
                        .filter { it.ativo?.contains(liveFilter, ignoreCase = true) == true }
                        .filter { liveDecisionFilter == "TODOS" || it.sinalExibicao == liveDecisionFilter }
                        .sortedWith { a, b ->
                            val res = when(liveSortKey) {
                                "hora" -> (a.hora ?: "").compareTo(b.hora ?: "")
                                "ativo" -> (a.ativo ?: "").compareTo(b.ativo ?: "")
                                "z" -> (a.zScore ?: 0.0).compareTo(b.zScore ?: 0.0)
                                else -> 0
                            }
                            if(liveSortAsc) res else -res
                        }
                    logs.forEach { log ->
                        key(log.id) {
                            LiveFlowCard(log, currentMarket, onTickerClick = onAssetAnalysisClick)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }

            // 2. CUSTÓDIA DE ATIVOS
            item {
                ExpandableSection(
                    title = "CUSTÓDIA DE ATIVOS",
                    count = uiState.carteira?.posicoes?.size ?: 0,
                    isExpanded = isCustodyExpanded,
                    onToggle = { isCustodyExpanded = !isCustodyExpanded },
                    filterQuery = custodyFilter,
                    onFilterChange = { custodyFilter = it },
                    filterContent = {
                        FilterRow(
                            selected = custodyPnLFilter,
                            options = listOf("TODOS", "LUCRO", "PREJUIZO"),
                            onSelect = { custodyPnLFilter = it }
                        )
                    },
                    headerContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeaderCell("ATIVO", 65.dp, isSorted = custodySortKey == "ticker", asc = custodySortAsc, onClick = { if(custodySortKey=="ticker") custodySortAsc=!custodySortAsc else {custodySortKey="ticker"; custodySortAsc=true} })
                            HeaderCell("QTD", 50.dp)
                            HeaderCell("CUSTO", 125.dp)
                            HeaderCell("MtM", 125.dp)
                            HeaderCell("LUCRO", 110.dp, isSorted = custodySortKey == "lucro", asc = custodySortAsc, onClick = { if(custodySortKey=="lucro") custodySortAsc=!custodySortAsc else {custodySortKey="lucro"; custodySortAsc=false} })
                            HeaderCell("RENT.", 90.dp)
                            HeaderCell("OPERAÇÃO", 90.dp)
                        }
                    }
                ) {
                    val assets = (uiState.carteira?.posicoes ?: emptyList())
                        .filter { it.ticker.contains(custodyFilter, ignoreCase = true) }
                        .filter { 
                            when(custodyPnLFilter) {
                                "LUCRO" -> it.lucro_prejuizo > 0
                                "PREJUIZO" -> it.lucro_prejuizo < 0
                                else -> true
                            }
                        }
                        .sortedWith { a, b ->
                            val res = when(custodySortKey) {
                                "ticker" -> a.ticker.compareTo(b.ticker)
                                "lucro" -> a.lucro_prejuizo.compareTo(b.lucro_prejuizo)
                                else -> 0
                            }
                            if(custodySortAsc) res else -res
                        }
                    assets.forEach { asset ->
                        key(asset.ticker) {
                            CustodyCard(asset, currentMarket, onTickerClick = onAssetAnalysisClick, onTradeClick = onTradeClick)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }

            // 3. AUDITORIA DE OPERAÇÕES (MtM)
            item {
                ExpandableSection(
                    title = "AUDITORIA DE OPERAÇÕES (MtM)",
                    count = uiState.auditoria.size,
                    isExpanded = isAuditoriaExpanded,
                    onToggle = { isAuditoriaExpanded = !isAuditoriaExpanded },
                    filterQuery = auditoriaFilter,
                    onFilterChange = { auditoriaFilter = it },
                    filterContent = {
                        FilterRow(
                            selected = auditoriaDecisionFilter,
                            options = listOf("TODOS", "COMPRA FORTE", "ALERTA DE VENDA", "NEUTRO"),
                            onSelect = { auditoriaDecisionFilter = it }
                        )
                    },
                    headerContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeaderCell("ATIVO", 65.dp, isSorted = auditoriaSortKey == "ativo", asc = auditoriaSortAsc, onClick = { if(auditoriaSortKey=="ativo") auditoriaSortAsc=!auditoriaSortAsc else {auditoriaSortKey="ativo"; auditoriaSortAsc=true} })
                            HeaderCell("PREÇO", 90.dp)
                            HeaderCell("Z-SCORE", 65.dp, isSorted = auditoriaSortKey == "z", asc = auditoriaSortAsc, onClick = { if(auditoriaSortKey=="z") auditoriaSortAsc=!auditoriaSortAsc else {auditoriaSortKey="z"; auditoriaSortAsc=true} })
                            HeaderCell("Δ VWAP", 75.dp)
                            HeaderCell("VaR (%)", 60.dp)
                            HeaderCell("SINAL", 100.dp)
                            HeaderCell("OPERAÇÃO", 90.dp)
                        }
                    }
                ) {
                    val audit = uiState.auditoria
                        .filter { it.ativo?.contains(auditoriaFilter, ignoreCase = true) == true }
                        .filter { auditoriaDecisionFilter == "TODOS" || it.sinal == auditoriaDecisionFilter }
                        .sortedWith { a, b ->
                            val res = when(auditoriaSortKey) {
                                "ativo" -> (a.ativo ?: "").compareTo(b.ativo ?: "")
                                "z" -> (a.zScore ?: 0.0).compareTo(b.zScore ?: 0.0)
                                else -> 0
                            }
                            if(auditoriaSortAsc) res else -res
                        }
                        .take(50) // 🛡️ LIMITA PARA NÃO TRAVAR A TELA (ANR)
                    
                    if (uiState.auditoria.isNotEmpty() && audit.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum ativo encontrado para este filtro.", color = textMuted, fontSize = 11.sp)
                        }
                    } else {
                        audit.forEach { row ->
                            key(row.id) {
                                AuditoriaCard(row, currentMarket, onTickerClick = onAssetAnalysisClick, onTradeClick = onTradeClick)
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }

            // 4. EXTRATO DE OPERAÇÕES
            item {
                val totalVolume = uiState.historico.sumOf { it.quantidade ?: 0 }
                ExpandableSection(
                    title = "EXTRATO DE OPERAÇÕES (Livro-Razão)",
                    count = uiState.historico.size,
                    isExpanded = isHistoryExpanded,
                    onToggle = { isHistoryExpanded = !isHistoryExpanded },
                    filterQuery = historyFilter,
                    onFilterChange = { historyFilter = it },
                    filterContent = {
                        FilterRow(
                            selected = historyTypeFilter,
                            options = listOf("TODOS", "COMPRA", "VENDA"),
                            onSelect = { historyTypeFilter = it }
                        )
                    },
                    headerContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeaderCell("Nº ORDEM", 80.dp)
                            HeaderCell("DATA / HORA", 140.dp)
                            HeaderCell("ATIVO", 65.dp)
                            HeaderCell("TIPO", 80.dp)
                            HeaderCell("QUANTIDADE", 70.dp)
                            HeaderCell("EXECUÇÃO", 90.dp)
                        }
                    }
                ) {
                    val history = uiState.historico
                        .filter { it.ticker?.contains(historyFilter, ignoreCase = true) == true }
                        .filter { historyTypeFilter == "TODOS" || it.tipoOrdem?.contains(historyTypeFilter, ignoreCase = true) == true }

                    if (history.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhuma operação encontrada.", color = textMuted, fontSize = 11.sp)
                        }
                    } else {
                        history.forEach { order ->
                            HistoryCard(order, currentMarket)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                        Surface(modifier = Modifier.fillMaxWidth(), color = cardBg.copy(alpha = 0.5f)) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("VOLUME TOTAL (EXIBIÇÃO)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("$totalVolume cotas", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 5. CARRINHO NOTURNO
            item {
                ExpandableSection(
                    title = "CARRINHO NOTURNO",
                    count = uiState.carrinho.size,
                    isExpanded = isCartExpanded,
                    onToggle = { isCartExpanded = !isCartExpanded },
                    filterQuery = cartFilter,
                    onFilterChange = { cartFilter = it },
                    headerContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeaderCell("ATIVO", 65.dp)
                            HeaderCell("AÇÃO", 80.dp)
                            HeaderCell("QTD TOTAL", 70.dp)
                            HeaderCell("PREÇO BASE", 90.dp)
                            HeaderCell("APROVAÇÃO", 120.dp)
                        }
                    }
                ) {
                    val cart = uiState.carrinho.filter { it.ticker.contains(cartFilter, ignoreCase = true) }
                    if (cart.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Carrinho vazio.", color = textMuted, fontSize = 11.sp)
                        }
                    } else {
                        cart.forEach { item ->
                            CartCard(item, currentMarket)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }

            // 6. PAINEL DE NOTÍCIAS (Por último e expansível)
            item {
                ExpandableSection(
                    title = "NOTÍCIAS (MUNDO & BRASIL)",
                    count = uiState.noticias.size,
                    isExpanded = isNewsExpanded,
                    onToggle = { isNewsExpanded = !isNewsExpanded }
                ) {
                    if (uiState.noticias.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryColor, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        uiState.noticias.forEach { news ->
                            key(news.id) {
                                NewsCard(news)
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun DynamicBullLogo(signalColor: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "BullLogoAnim")
    
    // Animação de pulso do brilho de fundo (Aura muito sutil e centralizada)
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    // Escala de "respiração" da logo
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreatheScale"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 1. Aura de Fundo (Círculo Perfeito e Menor para destaque central)
        Box(
            Modifier
                .size(80.dp) // Diminuído conforme solicitado
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(listOf(signalColor.copy(alpha = 0.4f), Color.Transparent)),
                    CircleShape
                )
        )

        // 2. A Imagem do Touro Original (MAIOR e SEM MÁSCARA GERAL)
        Image(
            painter = painterResource(id = R.drawable.bull_logo),
            contentDescription = "Market Bull",
            modifier = Modifier
                .fillMaxSize() 
                .scale(breatheScale),
            contentScale = ContentScale.Fit
        )
        
        // 🛡️ REMOVIDOS OS OLHOS EXTRAS PARA EVITAR DESALINHAMENTO 🛡️
        
        // 4. Vapor de Exaustão (Dispersão evidente e intensa)
        VaporParticleSystem(signalColor, infiniteTransition)
    }
}

@Composable
fun VaporParticleSystem(color: Color, transition: InfiniteTransition) {
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f, // Máxima intensidade
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VaporAlpha"
    )
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 85f, // 🌬️ DISPERSÃO EXTREMA
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VaporMove"
    )
    val spreadX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 30f, // 🌬️ ESPALHAMENTO LATERAL
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VaporSpread"
    )

    Box(Modifier.offset(y = 25.dp)) {
        // Partícula Esquerda
        Box(
            Modifier
                .offset(x = (-spreadX).dp, y = offsetY.dp)
                .size(24.dp) // PARTÍCULA MUITO GRANDE
                .alpha(0.9f - alpha)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.9f), Color.Transparent)), CircleShape)
        )
        // Partícula Direita
        Box(
            Modifier
                .offset(x = spreadX.dp, y = offsetY.dp)
                .size(24.dp)
                .alpha(0.9f - alpha)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.9f), Color.Transparent)), CircleShape)
        )
    }
}

@Composable
fun NewsCard(news: Noticia) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                news.link?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    context.startActivity(intent)
                }
            }
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(news.fonte, color = InfoColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(news.hora, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(news.titulo, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(news.resumo, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 3, lineHeight = 16.sp)
    }
}

@Composable
fun FilterRow(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(opt, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryColor,
                    selectedLabelColor = Color.Black,
                    labelColor = Color.White.copy(alpha = 0.6f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == opt,
                    borderColor = Color.White.copy(alpha = 0.1f),
                    selectedBorderColor = PrimaryColor
                )
            )
        }
    }
}

@Composable
fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, isSorted: Boolean = false, asc: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.width(width).let { if(onClick != null) it.clickable { onClick() } else it }, 
        verticalAlignment = Alignment.CenterVertically, 
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text, color = if(isSorted) Color.White else Color(0xFF64748B), fontSize = 8.5.sp, fontWeight = if(isSorted) FontWeight.Bold else FontWeight.Black)
        Icon(
            imageVector = if(isSorted) (if(asc) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown) else Icons.Default.UnfoldMore, 
            contentDescription = null, 
            tint = if(isSorted) PrimaryColor else Color(0xFF64748B).copy(alpha = 0.4f), 
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
fun ExpandableSection(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    filterQuery: String? = null,
    onFilterChange: ((String) -> Unit)? = null,
    filterContent: (@Composable () -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val textMuted = Color(0xFF94A3B8)
    var searchVisible by remember { mutableStateOf(false) }
    var filterVisible by remember { mutableStateOf(false) }
    
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Surface(color = Color(0xFF3B82F6).copy(alpha = 0.2f), shape = CircleShape) {
                    Text(count.toString(), color = Color(0xFF3B82F6), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onFilterChange != null) {
                    IconButton(onClick = { searchVisible = !searchVisible; if(!isExpanded) onToggle() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Search, "Pesquisar", tint = if(searchVisible) PrimaryColor else textMuted, modifier = Modifier.size(16.dp))
                    }
                }
                if (filterContent != null) {
                    IconButton(onClick = { filterVisible = !filterVisible; if(!isExpanded) onToggle() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.FilterList, "Filtro", tint = if(filterVisible) PrimaryColor else textMuted, modifier = Modifier.size(16.dp))
                    }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = textMuted)
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (searchVisible && onFilterChange != null) {
                        OutlinedTextField(
                            value = filterQuery ?: "",
                            onValueChange = onFilterChange,
                            placeholder = { Text("Pesquisar ativo...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().padding(8.dp).height(48.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    if (filterVisible && filterContent != null) {
                        filterContent()
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }

                    val horizontalScrollState = rememberScrollState()
                    if (headerContent != null) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).horizontalScroll(horizontalScrollState)) {
                            headerContent()
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                    val verticalScrollState = rememberScrollState()
                    Column(Modifier.padding(horizontal = 12.dp).verticalScroll(verticalScrollState).horizontalScroll(horizontalScrollState)) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun LiveFlowCard(log: LogMercado, currentMarket: String, onTickerClick: (String) -> Unit) {
    val colorCompra = Color(0xFF10B981)
    val colorVenda = Color(0xFFEF4444)
    val colorInfo = Color(0xFF3B82F6)
    val sColor = if(log.sinalExibicao.contains("COMPRA")) colorCompra else if(log.sinalExibicao.contains("VENDA")) colorVenda else colorInfo

    Column(Modifier.padding(vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(log.hora, Modifier.width(55.dp), color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(
                text = log.ativo ?: "---", 
                modifier = Modifier.width(60.dp).clickable { onTickerClick(log.ativo ?: "") },
                color = Color(0xFF3B82F6), fontSize = 15.sp, fontWeight = FontWeight.Black,
                textDecoration = TextDecoration.Underline
            )
            val symbol = if (currentMarket == "USD") "$" else "R$"
            Text("$symbol ${String.format(Locale.US, "%.2f", log.precoAtual ?: 0.0)}", Modifier.width(80.dp), color = colorInfo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.2f", log.zScore ?: 0.0), Modifier.width(50.dp), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("${String.format(Locale.US, "%.1f", log.riscoVar ?: 0.0)}%", Modifier.width(50.dp), color = colorVenda, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            val vwapColor = if((log.distVwap ?: 0.0) < 0) colorCompra else colorVenda
            Text("${if((log.distVwap ?: 0.0)>0) "+" else ""}${String.format(Locale.US, "%.2f", log.distVwap ?: 0.0)}%", Modifier.width(70.dp), color = vwapColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(String.format(Locale.US, "%.1f", log.volZScore ?: 0.0), Modifier.width(50.dp), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Surface(color = sColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                Text(log.sinalExibicao, color = sColor, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun AuditoriaCard(row: LogMercado, currentMarket: String, onTickerClick: (String) -> Unit, onTradeClick: (String, Double) -> Unit) {
    val colorCompra = Color(0xFF10B981)
    val colorVenda = Color(0xFFEF4444)
    val colorInfo = Color(0xFF3B82F6)
    val sinalAtivo = row.sinal ?: "NEUTRO"
    val sColor = if(sinalAtivo.contains("COMPRA")) colorCompra else if(sinalAtivo.contains("VENDA")) colorVenda else colorInfo

    Column(Modifier.padding(vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.ativo ?: "---", 
                modifier = Modifier.width(65.dp).clickable { onTickerClick(row.ativo ?: "") },
                color = Color(0xFF3B82F6), fontSize = 15.sp, fontWeight = FontWeight.Black,
                textDecoration = TextDecoration.Underline
            )
            val symbol = if (currentMarket == "USD") "$" else "R$"
            Text("$symbol ${String.format(Locale.US, "%.2f", row.precoAtual ?: 0.0)}", Modifier.width(90.dp), color = colorInfo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.2f", row.zScore ?: 0.0), Modifier.width(65.dp), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            val vwapColor = if((row.distVwap ?: 0.0) < 0) colorCompra else colorVenda
            Text("${if((row.distVwap ?: 0.0)>0) "+" else ""}${String.format(Locale.US, "%.2f", row.distVwap ?: 0.0)}%", Modifier.width(75.dp), color = vwapColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("${String.format(Locale.US, "%.1f", row.riscoVar ?: 0.0)}%", Modifier.width(60.dp), color = colorVenda, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Surface(Modifier.width(100.dp), color = sColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                Text(sinalAtivo, color = sColor, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
            }
            Button(
                onClick = { onTradeClick(row.ativo ?: "", row.precoAtual ?: 0.0) },
                modifier = Modifier.width(90.dp).height(28.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("Negociar", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun CustodyCard(asset: AtivoPatrimonio, moeda: String, onTickerClick: (String) -> Unit, onTradeClick: (String, Double) -> Unit) {
    val symbol = if (moeda == "USD") "$" else "R$"
    val colorCompra = Color(0xFF10B981)
    val colorVenda = Color(0xFFEF4444)
    val colorInfo = Color(0xFF3B82F6)
    val isPos = asset.lucro_prejuizo >= 0
    val rowColor = if(isPos) colorCompra else colorVenda

    Column(Modifier.padding(vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = asset.ticker, 
                modifier = Modifier.width(65.dp).clickable { onTickerClick(asset.ticker) },
                color = Color(0xFF3B82F6), fontSize = 16.sp, fontWeight = FontWeight.Black, 
                textDecoration = TextDecoration.Underline
            )
            Text("${asset.quantidade} un.", Modifier.width(50.dp), color = Color(0xFF94A3B8), fontSize = 12.sp)
            Text("$symbol ${String.format(Locale.GERMANY, "%.2f", asset.preco_medio)}", Modifier.width(125.dp), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("$symbol ${String.format(Locale.GERMANY, "%.2f", asset.preco_atual)}", Modifier.width(125.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${if(isPos) "+" else ""}$symbol ${String.format(Locale.GERMANY, "%.2f", asset.lucro_prejuizo)}", Modifier.width(110.dp), color = rowColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${if(isPos) "+" else ""}${String.format(Locale.US, "%.2f", asset.lucro_percentual)}%", Modifier.width(90.dp), color = rowColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { onTradeClick(asset.ticker, asset.preco_atual) },
                modifier = Modifier.width(90.dp).height(28.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorInfo)
            ) {
                Text("Negociar", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun HistoryCard(order: OrdemExecutada, moeda: String) {
    val symbol = if (moeda == "USD") "$" else "R$"
    val isCompra = order.tipoOrdem?.contains("COMPRA", ignoreCase = true) == true
    val color = if(isCompra) Color(0xFF10B981) else Color(0xFFEF4444)

    Column(Modifier.padding(vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${order.id}", Modifier.width(80.dp), color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(order.dataHora ?: "--:--", Modifier.width(140.dp), color = Color.White, fontSize = 10.sp)
            Text(order.ticker ?: "---", Modifier.width(65.dp), color = Color(0xFF3B82F6), fontSize = 14.sp, fontWeight = FontWeight.Black)
            Surface(Modifier.width(80.dp), color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(2.dp)) {
                Text(order.tipoOrdem ?: "---", color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
            }
            Text(order.quantidade.toString(), Modifier.width(70.dp), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            Text("$symbol ${String.format(Locale.US, "%.2f", order.precoExecucao ?: 0.0)}", Modifier.width(90.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CartCard(item: CarrinhoItem, moeda: String) {
    val symbol = if (moeda == "USD") "$" else "R$"
    val isCompra = item.tipo.contains("COMPRA")
    val color = if(isCompra) Color(0xFF10B981) else Color(0xFFEF4444)

    Column(Modifier.padding(vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.ticker, Modifier.width(65.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Surface(Modifier.width(80.dp), color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(2.dp)) {
                Text(item.tipo, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
            }
            Text(item.quantidade.toString(), Modifier.width(70.dp), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Text("$symbol ${String.format(Locale.US, "%.2f", item.preco)}", Modifier.width(90.dp), color = Color.White, fontSize = 11.sp)
            Row(Modifier.width(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}, Modifier.height(26.dp).weight(1f), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    Text("APROVAR", fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = {}, Modifier.size(24.dp).background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
