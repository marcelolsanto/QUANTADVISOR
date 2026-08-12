package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.ui.theme.*

import quantadvisor.com.br.data.model.TradeBacktest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    onBackClick: () -> Unit,
    viewModel: BacktestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Backtest Engine", color = PrimaryColor, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            
            // Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("ParÃ¢metros do Teste", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = uiState.ticker,
                        onValueChange = { viewModel.onTickerChange(it) },
                        label = { Text("Ticker do Ativo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor)
                    )
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Button(
                        onClick = { viewModel.rodarBacktest() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        else Text("INICIAR SIMULAÃ‡ÃƒO", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Results Section
            if (uiState.totalTrades > 0) {
                BacktestResultsView(uiState)
            } else if (uiState.errorMessage != null) {
                Text(uiState.errorMessage!!, color = VendaColor, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                Box(Modifier.fillMaxWidth().height(200.dp).background(SurfaceContainerLow, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("Aguardando definiÃ§Ã£o de ticker...", color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun BacktestResultsView(state: BacktestUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("RESULTADOS QUANTITATIVOS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "Win Rate", "${state.winRate}%", if(state.winRate > 50) CompraColor else AlertaColor)
            MetricCard(Modifier.weight(1f), "Total Trades", "${state.totalTrades}", InfoColor)
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                ResultRow("Drawdown MÃ¡ximo", "${state.maxDrawdown}%", VendaColor)
                // Outros campos poderiam ser adicionados se presentes no BacktestResponse
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("LISTAGEM DE TRADES", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)

        state.trades.forEach { trade ->
            TradeItem(trade)
        }
    }
}

@Composable
fun TradeItem(trade: TradeBacktest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(trade.date, color = TextMuted, fontSize = 10.sp)
                Text(trade.side, color = if (trade.side == "COMPRA") CompraColor else VendaColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("R$ ${trade.price}", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("${if(trade.result > 0) "+" else ""}${trade.result}%", color = if(trade.result > 0) CompraColor else VendaColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceContainer)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMuted, fontSize = 10.sp)
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}