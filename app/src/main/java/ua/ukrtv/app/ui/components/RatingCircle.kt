package ua.ukrtv.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RatingCircle(rating: Int, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    val color = when {
        rating >= 70 -> Color(0xFF21d07a)
        rating >= 40 -> Color(0xFFd2d531)
        else -> Color(0xFFdb2360)
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(42.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = 0.2f), style = Stroke(width = 3.dp.toPx()))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (rating / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        Text(
            text = rating.toString(),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun parseRating(rating: String?): Int {
    return rating?.filter { it.isDigit() }?.toIntOrNull() ?: 0
}
