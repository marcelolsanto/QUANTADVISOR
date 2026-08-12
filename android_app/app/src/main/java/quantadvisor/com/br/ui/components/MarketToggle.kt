package quantadvisor.com.br.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quantadvisor.com.br.ui.theme.*

@Composable
fun MarketToggle(
    currentMarket: String,
    onMarketChange: (String) -> Unit
) {
    val isBRL = currentMarket == "BRL"

    val offset by animateDpAsState(
        targetValue = if (isBRL) 4.dp else 120.dp,
        animationSpec = tween(durationMillis = 300), label = "offset_anim"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isBRL) CompraColor else InfoColor,
        animationSpec = tween(durationMillis = 300), label = "color_anim"
    )

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(36.dp)
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(30.dp))
            .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
            .clickable { onMarketChange(if (isBRL) "USD" else "BRL") },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(116.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .background(indicatorColor, RoundedCornerShape(26.dp))
        )

        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "🇧🇷 B3 (BRL)",
                    color = if (isBRL) Color.White else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "🇺🇸 NYSE (USD)",
                    color = if (!isBRL) Color.White else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
