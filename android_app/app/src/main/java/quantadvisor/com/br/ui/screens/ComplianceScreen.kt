package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceScreen(
    onBackClick: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    onCrmClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAccountingClick: () -> Unit = {},
    viewModel: ComplianceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "QuantAdvisor",
                        color = PrimaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = PrimaryColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAccountingClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "ContÃ¡bil",
                            tint = PrimaryColor
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = "TendÃªncia",
                            tint = PrimaryColor
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
                    selected = false,
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
            is ComplianceUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            }
            is ComplianceUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Erro: ${(uiState as ComplianceUiState.Error).message}", color = VendaColor)
                }
            }
            is ComplianceUiState.Success -> {
                val state = uiState as ComplianceUiState.Success
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header & Exemption Status
                    ComplianceHeaderSection(state)

                    // DARF Calculation Section
                    MonthlySummarySection(state)

                    // Ledger (Livro DiÃ¡rio)
                    LedgerSection()

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ComplianceHeaderSection(state: ComplianceUiState.Success) {
    val resumo = state.fiscalResumo
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Compliance & Fiscal",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = BorderStroke(1.dp, if(resumo?.isento_swing == true) CompraColor.copy(alpha = 0.3f) else VendaColor.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(if(resumo?.isento_swing == true) CompraColor else VendaColor, RoundedCornerShape(12.dp))
                )
                Icon(if(resumo?.isento_swing == true) Icons.Default.VerifiedUser else Icons.Default.ReportProblem, null, tint = if(resumo?.isento_swing == true) CompraColor else VendaColor)
                Column {
                    Text(
                        "STATUS DE ISENÃ‡ÃƒO (AÃ‡Ã•ES)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if(resumo?.isento_swing == true) "Dentro do Limite (R$ 20k)" else "Limite Excedido",
                        color = if(resumo?.isento_swing == true) CompraColor else VendaColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlySummarySection(state: ComplianceUiState.Success) {
    val r = state.fiscalResumo
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "RESUMO MENSAL - ${state.selectedAnoMes}",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TaxCard(
                title = "Swing Trade",
                taxRate = "15% IR",
                volume = "R$ ${String.format(Locale.GERMANY, "%,.2f", r?.volume_vendas_swing ?: 0.0)}",
                profit = "R$ ${String.format(Locale.GERMANY, "%,.2f", r?.lucro_realizado_swing ?: 0.0)}",
                taxToPay = "R$ ${String.format(Locale.GERMANY, "%,.2f", r?.imposto_swing ?: 0.0)}",
                accentColor = PrimaryColor
            )

            TaxCard(
                title = "Day Trade",
                taxRate = "20% IR",
                volume = "N/A",
                profit = "R$ ${String.format(Locale.GERMANY, "%,.2f", r?.lucro_realizado_daytrade ?: 0.0)}",
                taxToPay = "R$ ${String.format(Locale.GERMANY, "%,.2f", r?.imposto_dt ?: 0.0)}",
                accentColor = VendaColor
            )
        }

        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("EXPORTAR RELATÃ“RIO PDF", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TaxCard(
    title: String,
    taxRate: String,
    volume: String,
    profit: String,
    taxToPay: String,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(accentColor)
            )
            Column(Modifier.padding(16.dp).padding(start = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = SurfaceContainerHighest,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            taxRate,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                TaxRowItem("Volume Total", volume, TextPrimary)
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                TaxRowItem("Lucro Bruto", profit, CompraColor)
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("IR A PAGAR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(taxToPay, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun TaxRowItem(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun LedgerSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "LIVRO DIÃRIO (RECENTES)",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = BorderStroke(1.dp, OutlineVariant)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text("DATA/ATIVO", Modifier.weight(1.5f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("LANÃ‡AMENTO", Modifier.weight(1.5f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("STATUS B3", Modifier.weight(1.2f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                LedgerRowItem("PETR4", "24/10 10:15", "- R$ 35,40 (D)", VendaColor, "Liquidado", TextMuted)
                LedgerRowItem("VALE3", "24/10 14:30", "+ R$ 120,50 (C)", CompraColor, "Pendente", AlertaColor)
                LedgerRowItem("ITUB4", "23/10 09:45", "+ R$ 45,00 (C)", CompraColor, "Liquidado", TextMuted)
            }
        }
    }
}

@Composable
fun LedgerRowItem(
    ticker: String,
    time: String,
    value: String,
    valueColor: Color,
    status: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, OutlineVariant.copy(alpha = 0.2f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1.5f)) {
            Text(ticker, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(time, color = TextMuted, fontSize = 11.sp)
        }
        Text(
            value,
            Modifier.weight(1.5f),
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace
        )
        Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
            Surface(
                color = SurfaceContainerHighest,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    status,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}