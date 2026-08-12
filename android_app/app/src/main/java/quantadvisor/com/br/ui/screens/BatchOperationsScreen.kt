package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.CarrinhoItem
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOperationsScreen(
    onBackClick: () -> Unit,
    viewModel: BatchOperationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Operações em Lote", fontWeight = FontWeight.Bold, color = PrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            if (uiState.cartItems.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.executeBatch() },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    text = { Text("Executar Lote", fontWeight = FontWeight.Black) },
                    containerColor = CompraColor,
                    contentColor = Color.Black
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                AuditSummaryCard(
                    assetsMonitored = uiState.auditoria?.ativosMonitorados ?: 0,
                    activeSignals = uiState.auditoria?.sinaisAtivos ?: 0,
                    lastScan = uiState.auditoria?.ultimaVarredura ?: "---"
                )
            }

            item {
                Text(
                    "Carrinho Noturno",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.cartItems.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhuma ordem pendente no carrinho.", color = TextMuted)
                    }
                }
            } else {
                items(uiState.cartItems) { item ->
                    CartItemRow(item)
                }
            }
            
            uiState.message?.let { msg ->
                item {
                    Surface(
                        color = PrimaryColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PrimaryColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        if (uiState.isExecuting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryColor)
            }
        }
    }
}

@Composable
fun AuditSummaryCard(assetsMonitored: Int, activeSignals: Int, lastScan: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = InfoColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("STATUS DA AUDITORIA", fontWeight = FontWeight.Black, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Ativos Monitorados", color = TextMuted, fontSize = 11.sp)
                    Text(assetsMonitored.toString(), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sinais Ativos", color = TextMuted, fontSize = 11.sp)
                    Text(activeSignals.toString(), color = CompraColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Última Varredura: $lastScan", color = TextMuted, fontSize = 10.sp, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
fun CartItemRow(item: CarrinhoItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle, 
                    null, 
                    tint = if(item.tipo == "COMPRA") CompraColor else VendaColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.ticker, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${item.tipo} • ${item.quantidade} un.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("R$ ${String.format(Locale.GERMANY, "%,.2f", item.preco)}", color = TextPrimary, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
    }
}
