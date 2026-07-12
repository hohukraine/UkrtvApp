package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.domain.model.Provider

@Composable
fun PhoneProviderSwitcher(
    providers: List<Provider>,
    currentProviderId: String,
    brandColor: Color,
    onProviderClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (providers.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        providers.forEach { provider ->
            val isSelected = provider.name == currentProviderId

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) brandColor else Color(0xFF1A1A1D),
                animationSpec = tween(250),
                label = "providerBg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                animationSpec = tween(250),
                label = "providerText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onProviderClick(provider.name) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = provider.name,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

private val Background = Color(0xFF080808)
