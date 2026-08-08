package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(
    title: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    isPhone: Boolean = false
) {
    if (title.isEmpty()) return

    val titleColor = Color(0xFFF2F2F2)
    val barColor = remember(brandColor) { brandColor.copy(alpha = 0.95f) }

    val barWidth = if (isPhone) 3.dp else 4.dp
    val barHeight = if (isPhone) 16.dp else 20.dp
    val spacing = if (isPhone) 8.dp else 12.dp
    val fontSize = if (isPhone) 13.sp else 20.sp
    val letterSpacing = if (isPhone) 0.5.sp else 1.sp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .background(barColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(spacing))
        Text(
            text = title.uppercase(),
            color = titleColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            letterSpacing = letterSpacing
        )
        if (!isPhone) {
            Spacer(modifier = Modifier.width(spacing * 2))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
        }
    }
}
