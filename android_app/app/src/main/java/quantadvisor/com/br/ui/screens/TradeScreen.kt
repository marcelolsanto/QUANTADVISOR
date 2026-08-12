package quantadvisor.com.br.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    ticker: String,
    initialPrice: Double,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    var quantity by remember { mutableStateOf("100") }
    var orderType by remember { mutableStateOf("COMPRA") }
    val totalPrice = (quantity.toDoubleOrNull() ?: 0.0) * initialPrice

    Scaffold(
        containerColor = BgBackground,
        topBar = {
            TopAppBar(
                title = { Text("Boleta de Negociação", fontWeight = FontWeight.Bold, color = PrimaryColor) },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ticker, color = PrimaryColor, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "Preço: R$ ${String.format(Locale.GERMANY, "%,.2f", initialPrice)}",
                        color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { orderType = "COMPRA" },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orderType == "COMPRA") CompraColor else SurfaceContainer,
                        contentColor = if (orderType == "COMPRA") Color.Black else TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("COMPRA", fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = { orderType = "VENDA" },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orderType == "VENDA") VendaColor else SurfaceContainer,
                        contentColor = if (orderType == "VENDA") Color.White else TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("VENDA", fontWeight = FontWeight.Black)
                }
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Quantidade (Cotas)", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryColor,
                    unfocusedBorderColor = OutlineVariant
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgBackground),
                border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VALOR TOTAL ESTIMADO", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "R$ ${String.format(Locale.GERMANY, "%,.2f", totalPrice)}",
                        color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSuccess() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(orderType == "COMPRA") CompraColor else VendaColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("EXECUTAR ${orderType}", fontWeight = FontWeight.Black, color = if(orderType=="COMPRA") Color.Black else Color.White)
            }
        }
    }
}
