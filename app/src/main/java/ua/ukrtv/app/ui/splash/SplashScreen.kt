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

private const val FADE_IN_DURATION_MS = 400
private const val FADE_OUT_DURATION_MS = 400
private const val COLOR_CYCLE_MS = 1200

@Composable
fun SplashScreen(
    providerColor: Color,
    dismiss: Boolean,
    onFinished: () -> Unit
) {
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
                durationMillis = COLOR_CYCLE_MS
                colors.forEachIndexed { i, color ->
                    color.at(i * COLOR_CYCLE_MS / colors.size).using(LinearEasing)
                }
                colors[0].at(COLOR_CYCLE_MS)
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "tv_color"
    )

    var visible by remember { mutableStateOf(false) }
    var fadingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(dismiss) {
        if (dismiss) {
            fadingOut = true
            delay(FADE_OUT_DURATION_MS.toLong())
            onFinished()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (fadingOut) 0f else (if (visible) 1f else 0f),
        animationSpec = tween(FADE_IN_DURATION_MS),
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
