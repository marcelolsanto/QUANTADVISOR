package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.AssetAnalysis
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetAnalysisScreen(
    onBack: () -> Unit,
    viewModel: AssetAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Raio-X Institucional", fontWeight = FontWeight.Bold, color = PrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
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

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            } else if (uiState.error != null) {
                Surface(color = VendaColor.copy(alpha = 0.1f), border = BorderStroke(1.dp, VendaColor), shape = RoundedCornerShape(8.dp)) {
                    Text("Erro: ${uiState.error}", color = VendaColor, modifier = Modifier.padding(16.dp))
                }
            } else if (uiState.asset != null) {
                AssetDetailCard(uiState.asset!!)
            } else {
                Text(
                    "Busque um ativo para ver a anÃ¡lise quantitativa completa.",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AssetDetailCard(asset: AssetAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(asset.ticker, color = PrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("R$ ${String.format(Locale.GERMANY, "%,.2f", asset.preco_atual)}", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    val color = if (asset.variacao_dia >= 0) CompraColor else VendaColor
                    Text("${if (asset.variacao_dia >= 0) "+" else ""}${String.format(Locale.US, "%.2f", asset.variacao_dia)}%", color = color, fontWeight = FontWeight.Bold)
                }
                AIDecisionBadge(asset.ai_decision, asset.ai_score)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = OutlineVariant.copy(alpha = 0.2f))

            Text("MÃ‰TRICAS FUNDAMENTAIS", color = TextMuted, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem("P/E", String.format(Locale.US, "%.2f", asset.p_e), Modifier.weight(1f))
                MetricItem("P/VP", String.format(Locale.US, "%.2f", asset.p_vp), Modifier.weight(1f))
                MetricItem("DY", "${String.format(Locale.US, "%.2f", asset.dividend_yield)}%", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem("ROE", "${String.format(Locale.US, "%.2f", asset.roe)}%", Modifier.weight(1f))
                MetricItem("MARGEM", "${String.format(Locale.US, "%.2f", asset.margem_liquida)}%", Modifier.weight(1f))
                MetricItem("DIV/EBITDA", String.format(Locale.US, "%.2f", asset.divida_ebitda), Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = OutlineVariant.copy(alpha = 0.2f))

            Text("ANÃLISE QUANTITATIVA", color = TextMuted, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem("Z-SCORE", String.format(Locale.US, "%.2f", asset.z_score), Modifier.weight(1f))
                MetricItem("VOLATILIDADE", "${String.format(Locale.US, "%.2f", asset.volatilidade)}%", Modifier.weight(1f))
                MetricItem("BETA", String.format(Locale.US, "%.2f", asset.beta), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AIDecisionBadge(decision: String, score: Int) {
    val color = when(decision.uppercase()) {
        "COMPRA FORTE", "COMPRA" -> CompraColor
        "VENDA", "VENDA FORTE" -> VendaColor
        else -> InfoColor
    }

    Column(horizontalAlignment = Alignment.End) {
        Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
            Text(decision.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Text("AI SCORE: $score/100", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}