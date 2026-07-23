package ua.ukrtv.app.ui.splash

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 3000L
private const val FADE_IN_DURATION_MS = 1000

@Composable
fun SplashScreen(
    providerColor: Color,
    onSplashFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    // Cycle through provider colors for the "TV" part
    val colors = listOf(
        Color(0xFF31C469), // Eneyida
        Color(0xFFCA563F), // Uakino
        providerColor,      // Selected/Default
        Color(0xFF6E85B7)  // BrandBlue
    ).distinct()

    val infiniteTransition = rememberInfiniteTransition(label = "logo_color_cycle")
    val animatedColor by infiniteTransition.animateColor(
        initialValue = colors[0],
        targetValue = colors[0],
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                var i = 0
                colors.forEach { color ->
                    color.at(i * (3000 / colors.size)).using(LinearEasing)
                    i++
                }
                colors[0].at(3000)
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "tv_color"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_DURATION_MS)
        onSplashFinished()
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_IN_DURATION_MS),
        label = "splash_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UKR",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "TV",
                color = animatedColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        }
    }
}
