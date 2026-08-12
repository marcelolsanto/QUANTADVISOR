package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import quantadvisor.com.br.data.model.AtivoPatrimonio
import quantadvisor.com.br.ui.components.MarketToggle
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onBackClick: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMarket by viewModel.marketSession.currentMarket.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Patrimônio & Carteira", fontWeight = FontWeight.Bold, color = PrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MarketToggle(
                            currentMarket = currentMarket,
                            onMarketChange = { viewModel.marketSession.setMarket(it) }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                item {
                    WealthSummaryCard(
                        totalEquity = uiState.totalEquity,
                        freeCash = if(currentMarket == "USD") (uiState.user?.saldo_usd ?: 0.0) else (uiState.user?.saldo_disponivel ?: 0.0),
                        accumulatedProfit = uiState.totalPl,
                        moeda = currentMarket
                    )
                }

                item {
                    Text(
                        "Ativos em Carteira ($currentMarket)",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.assets) { asset ->
                    AssetHoldingItem(asset, currentMarket)
                }
            }
        }
    }
}

@Composable
fun WealthSummaryCard(totalEquity: Double, freeCash: Double, accumulatedProfit: Double, moeda: String) {
    val symbol = if (moeda == "USD") "$" else "R$"
    val locale = if (moeda == "USD") Locale.US else Locale.GERMANY

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Patrimônio Total ($moeda)",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Text(
                "$symbol ${String.format(locale, "%,.2f", totalEquity)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "Caixa Livre",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        "$symbol ${String.format(locale, "%,.2f", freeCash)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CompraColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Lucro Realizado (P&L)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        "$symbol ${String.format(locale, "%,.2f", accumulatedProfit)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (accumulatedProfit >= 0) CompraColor else VendaColor
                    )
                }
            }
        }
    }
}

@Composable
fun AssetHoldingItem(asset: AtivoPatrimonio, moeda: String) {
    val symbol = if (moeda == "USD") "$" else "R$"
    val locale = if (moeda == "USD") Locale.US else Locale.GERMANY

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(asset.ticker, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${asset.quantidade} un. • PM: $symbol ${String.format(locale, "%,.2f", asset.preco_medio)}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$symbol ${String.format(locale, "%,.2f", asset.quantidade * asset.preco_atual)}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${if (asset.lucro_prejuizo >= 0) "+" else ""}$symbol ${String.format(locale, "%,.2f", asset.lucro_prejuizo)}",
                    color = if (asset.lucro_prejuizo >= 0) CompraColor else VendaColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
