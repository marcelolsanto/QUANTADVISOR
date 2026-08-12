package quantadvisor.com.br.ui.screens.terminal

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.BiometricHelper
import quantadvisor.com.br.data.model.UsuarioResumo
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
    onAssetAnalysisClick: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Paleta de Cores Institucional (Sincronizada com o snippet do usuÃ¡rio)
    val bgDark = Color(0xFF0B1326) // BgBackground
    val cardBg = Color(0xFF171F33) // SurfaceContainer
    val textMain = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF94A3B8)
    val colorCompra = Color(0xFF10B981) // CompraColor
    val colorVenda = Color(0xFFEF4444)  // VendaColor
    val colorInfo = Color(0xFF3B82F6)

    LaunchedEffect(userIdFromNav) {
        viewModel.initTerminal(userIdFromNav)
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().background(bgDark), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryColor)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgDark)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // HEADER MACRO
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = textMain)
                    }
                    Text("ðŸ›ï¸ VisÃ£o Institucional", color = textMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onAssetAnalysisClick) { Icon(Icons.Default.Search, "Raio-X", tint = textMuted) }
                    IconButton(onClick = onNewsClick) { Icon(Icons.Default.Public, "News", tint = textMuted) }
                    
                    Box(Modifier.size(8.dp).background(if(uiState.isConnected) colorCompra else colorVenda, CircleShape))
                    Text(if(uiState.isConnected) "Online" else "Offline", color = textMuted, fontSize = 11.sp)
                }
            }

            // FEEDBACK DE ORDEM
            uiState.orderStatus?.let { status ->
                Surface(
                    color = colorInfo.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colorInfo),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(status, color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                }
            }

            // 1. POSIÃ‡ÃƒO CONSOLIDADA (PATRIMÃ”NIO, CAIXA, P&L)
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PosiÃ§Ã£o Consolidada Atual", color = textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "R$ ${String.format(Locale.GERMANY, "%,.2f", uiState.dashboard?.patrimonio_total ?: 0.0)}",
                        color = colorInfo,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("CAIXA LIVRE", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "R$ ${String.format(Locale.GERMANY, "%,.2f", uiState.selectedUser?.saldo_disponivel ?: 0.0)}",
                                color = colorCompra,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("P&L TOTAL (LUCRO)", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            val pnl = uiState.selectedUser?.lucro_acumulado ?: 0.0
                            Text(
                                text = "${if(pnl >= 0) "+" else ""}R$ ${String.format(Locale.GERMANY, "%,.2f", pnl)}",
                                color = if(pnl >= 0) colorCompra else colorVenda,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 2. STATUS DE MERCADO (REGIME & DRAWDOWN)
            val regime = uiState.regime
            val regimeColor = if (regime.contains("BEAR")) colorVenda else colorCompra
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Regime HMM", color = textMuted, fontSize = 11.sp)
                        Text(if (regime.contains("BEAR")) "ðŸ» $regime" else "ðŸ‚ $regime", color = regimeColor, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Max Drawdown", color = textMuted, fontSize = 11.sp)
                        Text("${uiState.drawdown}%", color = colorVenda, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. CHAVE MESTRA: PILOTO IA
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ðŸ¤– Piloto AutomÃ¡tico", color = textMain, fontWeight = FontWeight.Bold)
                        Text(if (uiState.pilotoAutomatico) "Roteamento HFT Ativo" else "Apenas Alertas", color = textMuted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.togglePiloto() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.pilotoAutomatico) colorCompra.copy(alpha = 0.2f) else colorVenda.copy(alpha = 0.2f),
                            contentColor = if (uiState.pilotoAutomatico) colorCompra else colorVenda
                        ),
                        shape = RoundedCornerShape(30.dp),
                        border = BorderStroke(1.dp, if (uiState.pilotoAutomatico) colorCompra else colorVenda)
                    ) {
                        Text(if (uiState.pilotoAutomatico) "LIGADO" else "PAUSADO", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. CUSTÃ“DIA CONSOLIDADA (Ativos Reais da API)
            ActiveCustodySection(uiState)

            Spacer(modifier = Modifier.height(16.dp))

            // 5. MINI-TERMINAL STREAMING (A Fita Matrix)
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ðŸ“¡ Streaming (Live)", color = textMain, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onBacktestClick, contentPadding = PaddingValues(0.dp)) {
                                Text("BACKTEST", color = colorInfo, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            TextButton(onClick = onMonteCarloClick, contentPadding = PaddingValues(0.dp)) {
                                Text("SIMULAÃ‡ÃƒO MC", color = colorInfo, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black).padding(12.dp)
                    ) {
                        LazyColumn {
                            items(uiState.logs) { log ->
                                val sColor = if(log.sinal.contains("COMPRA")) colorCompra else if(log.sinal.contains("VENDA")) colorVenda else textMuted
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        text = "[${log.hora}] âš¡ ${log.sinal} | ${log.ativo} | Z: ${String.format(Locale.US, "%.2f", log.zScore ?: 0.0)}",
                                        color = sColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. BOTÃƒO DE PÃ‚NICO (CIRCUIT BREAKER)
            Button(
                onClick = { viewModel.circuitBreaker() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorVenda),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isSendingOrder
            ) {
                if (uiState.isSendingOrder) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("ðŸ›‘ CIRCUIT BREAKER (ZERAR CUSTÃ“DIA)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ActiveCustodySection(state: TerminalUiState) {
    val assets = state.carteira?.posicoes ?: emptyList()
    val cardBg = Color(0xFF171F33)
    val textMain = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF94A3B8)
    val colorCompra = Color(0xFF10B981)
    val colorVenda = Color(0xFFEF4444)

    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("CUSTÃ“DIA CONSOLIDADA", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            if (assets.isNotEmpty()) {
                Text("${assets.size} Ativos", color = PrimaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(), 
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(12.dp)) {
                if (assets.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum ativo em custÃ³dia.", color = textMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("Ativo", Modifier.weight(1f), color = textMuted, fontSize = 10.sp)
                        Text("Qtd", Modifier.width(60.dp), color = textMuted, fontSize = 10.sp, textAlign = TextAlign.End)
                        Text("MtM", Modifier.width(80.dp), color = textMuted, fontSize = 10.sp, textAlign = TextAlign.End)
                        Text("P&L", Modifier.width(60.dp), color = textMuted, fontSize = 10.sp, textAlign = TextAlign.End)
                    }
                    assets.forEach { asset ->
                        val isPos = asset.lucro_prejuizo >= 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(asset.ticker, Modifier.weight(1f), color = PrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(asset.quantidade.toString(), Modifier.width(60.dp), color = textMain, fontSize = 12.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
                            Text("R$ ${String.format(Locale.GERMANY, "%,.2f", asset.preco_atual)}", Modifier.width(80.dp), color = textMain, fontSize = 12.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
                            
                            Text("${if(isPos) "+" else ""}${String.format(Locale.US, "%.1f", asset.lucro_percentual)}%", Modifier.width(60.dp), color = if(isPos) colorCompra else colorVenda, fontSize = 12.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}