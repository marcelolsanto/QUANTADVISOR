package quantadvisor.com.br

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import quantadvisor.com.br.ui.screens.login.OtpViewModel
import quantadvisor.com.br.ui.screens.login.OtpUiState

// --- CORES DA TELA ---
private object OtpThemeColors {
    val BgBackground = Color(0xFF0B1326)
    val SurfaceContainer = Color(0xFF171F33)
    val SurfaceBright = Color(0xFF31394D)
    val SurfaceContainerHigh = Color(0xFF222A3D)
    val OutlineVariant = Color(0xFF424754)
    val OutlineColor = Color(0xFF8C909F)
    val PrimaryColor = Color(0xFFADC6FF)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFF94A3B8)
    val CompraColor = Color(0xFF10B981)
    val VendaColor = Color(0xFFEF4444)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpValidationScreen(
    email: String,
    codigoDevRecebido: String? = null,
    onValidationSuccess: () -> Unit = {},
    onResendCode: () -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: OtpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Estado para os 6 dÃ­gitos individuais
    val otpDigits = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    val codigoDevFormatado = remember(codigoDevRecebido) {
        val raw = codigoDevRecebido?.filter { it.isDigit() } ?: ""
        if (raw.length == 6) "${raw.substring(0, 3)}-${raw.substring(3)}" else raw
    }

    fun preencherCodigo(codigo: String) {
        val limpo = codigo.filter { it.isDigit() }.take(6)
        for (i in 0 until 6) {
            otpDigits[i] = if (i < limpo.length) limpo[i].toString() else ""
        }
    }

    LaunchedEffect(uiState.isValidationSuccess) {
        if (uiState.isValidationSuccess) onValidationSuccess()
    }

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OtpThemeColors.BgBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Marca / Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = OtpThemeColors.PrimaryColor, modifier = Modifier.size(32.dp))
                Text("QuantAdvisor", color = OtpThemeColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }

            // Card Principal
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, OtpThemeColors.OutlineVariant, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OtpThemeColors.SurfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(OtpThemeColors.SurfaceContainerHigh).border(1.dp, OtpThemeColors.OutlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LockOpen, null, tint = OtpThemeColors.CompraColor, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ative sua Conta", color = OtpThemeColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Enviamos um cÃ³digo de 6 dÃ­gitos para o seu WhatsApp. Digite-o abaixo.", color = OtpThemeColors.TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }

                    // Mensagens de Alerta
                    uiState.errorMessage?.let { msg ->
                        Surface(color = OtpThemeColors.VendaColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, OtpThemeColors.VendaColor), modifier = Modifier.fillMaxWidth()) {
                            Text("âš ï¸ $msg", color = OtpThemeColors.VendaColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                        }
                    }

                    uiState.successMessage?.let { msg ->
                        Surface(color = OtpThemeColors.CompraColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, OtpThemeColors.CompraColor), modifier = Modifier.fillMaxWidth()) {
                            Text("âœ… $msg", color = OtpThemeColors.CompraColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                        }
                    }

                    // 6 Caixas de Input
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until 6) {
                            OutlinedTextField(
                                value = otpDigits[i],
                                onValueChange = { value ->
                                    val digitsOnly = value.filter { it.isDigit() }
                                    if (digitsOnly.length > 1) {
                                        preencherCodigo(digitsOnly)
                                        focusRequesters[minOf(digitsOnly.length, 5)].requestFocus()
                                    } else {
                                        otpDigits[i] = digitsOnly.take(1)
                                        if (digitsOnly.isNotEmpty() && i < 5) focusRequesters[i + 1].requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 3.dp).height(64.dp).focusRequester(focusRequesters[i]).onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                                        if (otpDigits[i].isEmpty() && i > 0) {
                                            focusRequesters[i - 1].requestFocus()
                                            otpDigits[i - 1] = ""
                                            true
                                        } else false
                                    } else false
                                },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (i == 5) ImeAction.Done else ImeAction.Next),
                                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OtpThemeColors.CompraColor, unfocusedBorderColor = OtpThemeColors.OutlineColor, focusedContainerColor = OtpThemeColors.SurfaceBright, unfocusedContainerColor = OtpThemeColors.SurfaceBright, cursorColor = OtpThemeColors.CompraColor),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Dev Log
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, OtpThemeColors.OutlineVariant, RoundedCornerShape(6.dp)), shape = RoundedCornerShape(6.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("SYSTEM LOG :: DEV MODE", color = OtpThemeColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OtpThemeColors.VendaColor))
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Yellow))
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OtpThemeColors.CompraColor))
                                }
                            }
                            HorizontalDivider(color = OtpThemeColors.OutlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                            if (!codigoDevRecebido.isNullOrEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { preencherCodigo(codigoDevRecebido) }) {
                                    Text("> Extracted Code: ", color = OtpThemeColors.CompraColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Surface(color = OtpThemeColors.CompraColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text(codigoDevFormatado, color = OtpThemeColors.CompraColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }

                    // BotÃ£o Principal
                    Button(
                        onClick = {
                            val code = otpDigits.joinToString("")
                            if (code.length == 6) viewModel.validar(email, code)
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OtpThemeColors.CompraColor, contentColor = Color.White)
                    ) {
                        if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text("ATIVAR CONTA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Links
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onResendCode() }) {
                            Icon(Icons.Default.Refresh, null, tint = OtpThemeColors.TextMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reenviar cÃ³digo", color = OtpThemeColors.TextMuted, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBackClick() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OtpThemeColors.TextMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Voltar", color = OtpThemeColors.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}