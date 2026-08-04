package ua.ukrtv.app.ui.splash

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
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

private const val DEFAULT_DURATION_MS = 1200L
private const val FADE_IN_DURATION_MS = 400
private const val FADE_OUT_DURATION_MS = 400

@Composable
fun SplashScreen(
    providerColor: Color,
    durationMs: Long = DEFAULT_DURATION_MS,
    onSplashFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var fadingOut by remember { mutableStateOf(false) }

    val colors = listOf(
        Color(0xFF31C469), // Eneyida
        Color(0xFFCA563F), // Uakino
        providerColor,      // Selected/Default
        Color(0xFF6E85B7)  // BrandBlue
    ).distinct()

    val cycleMs = durationMs.toInt().coerceAtLeast(400)

    val infiniteTransition = rememberInfiniteTransition(label = "logo_color_cycle")
    val animatedColor by infiniteTransition.animateColor(
        initialValue = colors[0],
        targetValue = colors[0],
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleMs
                colors.forEachIndexed { i, color ->
                    color.at(i * cycleMs / colors.size).using(LinearEasing)
                }
                colors[0].at(cycleMs)
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "tv_color"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(durationMs)
        fadingOut = true
        delay(FADE_OUT_DURATION_MS.toLong())
        onSplashFinished()
    }

    val alpha by animateFloatAsState(
        targetValue = if (fadingOut) 0f else (if (visible) 1f else 0f),
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
