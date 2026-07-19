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

    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }
    
    val barWidth = 3.dp
    val barHeight = if (isPhone) 14.dp else 18.dp
    val spacing = if (isPhone) 8.dp else 10.dp
    val fontSize = if (isPhone) 12.sp else 13.sp
    val letterSpacing = if (isPhone) 1.sp else 2.sp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .background(brandColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(spacing))
        Text(
            text = title.uppercase(),
            color = titleColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            letterSpacing = letterSpacing
        )
    }
}
