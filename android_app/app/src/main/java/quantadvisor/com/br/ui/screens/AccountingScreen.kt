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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.data.model.ItemLancamento
import quantadvisor.com.br.data.model.LoteFiscal
import quantadvisor.com.br.ui.theme.*
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(
    onBackClick: () -> Unit,
    viewModel: AccountingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Gestão Contábil", color = PrimaryColor, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        }
    ) { padding ->
        when (uiState) {
            is AccountingUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            }
            is AccountingUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Erro: ${(uiState as AccountingUiState.Error).message}", color = VendaColor)
                }
            }
            is AccountingUiState.Success -> {
                val state = uiState as AccountingUiState.Success
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    // Tabs: Lançamentos vs Lotes Fiscais
                    SecondaryTabRow(
                        selectedTabIndex = state.selectedTab,
                        containerColor = SurfaceContainer,
                        contentColor = PrimaryColor,
                        divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(state.selectedTab),
                                color = PrimaryColor
                            )
                        }
                    ) {
                        Tab(selected = state.selectedTab == 0, onClick = { viewModel.onTabSelected(0) }) {
                            Text("Livro-Razão", modifier = Modifier.padding(12.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Tab(selected = state.selectedTab == 1, onClick = { viewModel.onTabSelected(1) }) {
                            Text("Lotes Fiscais", modifier = Modifier.padding(12.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    if (state.selectedTab == 0) {
                        LancamentosList(state.lancamentos)
                    } else {
                        LotesList(state.lotes)
                    }
                }
            }
        }
    }
}

@Composable
fun LancamentosList(list: List<ItemLancamento>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) {
            item { Text("Nenhum lançamento contábil encontrado.", color = TextMuted, modifier = Modifier.padding(16.dp)) }
        } else {
            items(list) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(item.historico, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("R$ ${String.format(Locale.GERMANY, "%,.2f", item.valor)}", color = PrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Column {
                                Text("DÉBITO: ${item.debito}", color = VendaColor.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("CRÉDITO: ${item.credito}", color = CompraColor.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            // Oculta a data se for o padrão zero do Go
                            val dataExibicao = if (item.data.startsWith("0001")) "Pendente" else item.data.take(10)
                            Text(dataExibicao, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LotesList(list: List<LoteFiscal>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) {
            item { Text("Nenhum lote fiscal em aberto.", color = TextMuted, modifier = Modifier.padding(16.dp)) }
        } else {
            items(list) { lote ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    border = BorderStroke(0.5.dp, OutlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(lote.ticker, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Surface(color = PrimaryContainer.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                val dataExibicao = if (lote.dataEntrada.startsWith("0001")) "Hoje" else lote.dataEntrada.take(10)
                                Text("Entrada: $dataExibicao", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            LoteMetric("Qtd Original", lote.quantidadeInicial.toString())
                            LoteMetric("Saldo Atual", lote.quantidadeAtual.toString())
                            LoteMetric("Preço Médio", "R$ ${String.format(Locale.GERMANY, "%,.2f", lote.precoCompra)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoteMetric(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
