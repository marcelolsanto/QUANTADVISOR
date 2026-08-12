package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.ui.theme.*

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonteCarloScreen(
    onBackClick: () -> Unit,
    viewModel: MonteCarloViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Monte Carlo Simulation", color = PrimaryColor, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            
            // Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Configurações Probabilísticas", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = uiState.ticker,
                        onValueChange = { viewModel.onTickerChange(it) },
                        label = { Text("Ativo Alvo") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor)
                    )
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Button(
                        onClick = { viewModel.rodarSimulacao() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        if (uiState.isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        else Text("EXECUTAR 10.000 CENÁRIOS", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Probability Results
            if (uiState.result != null) {
                MonteCarloResultsView(uiState.densityPoints)
            } else if (uiState.errorMessage != null) {
                Text(uiState.errorMessage!!, color = VendaColor, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun MonteCarloResultsView(densityPoints: List<Pair<Float, Float>>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("DISTRIBUIÇÃO DE PROBABILIDADES", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                ProbabilityRow("Probabilidade de Ganho", "74.2%", CompraColor)
                ProbabilityRow("Retorno Esperado (Médio)", "+18.4%", CompraColor)
                ProbabilityRow("Pior Cenário (VaR 99%)", "-12.5%", VendaColor)
                ProbabilityRow("Melhor Cenário", "+45.8%", InfoColor)
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
        ) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                if (densityPoints.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path()
                        val maxX = densityPoints.maxOf { it.first }
                        val minX = densityPoints.minOf { it.first }
                        val maxY = densityPoints.maxOf { it.second }
                        
                        val rangeX = maxX - minX
                        
                        densityPoints.forEachIndexed { index, point ->
                            val x = (point.first - minX) / rangeX * size.width
                            val y = size.height - (point.second / maxY * size.height)
                            
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        
                        drawPath(
                            path = path,
                            color = PrimaryColor,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                } else {
                    Text("Gerando curva de densidade...", color = TextMuted, fontStyle = FontStyle.Italic, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ProbabilityRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextPrimary, fontSize = 13.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}
