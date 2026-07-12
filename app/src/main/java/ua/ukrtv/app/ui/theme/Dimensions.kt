package ua.ukrtv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Shapes ──
object Shapes {
    val card = RoundedCornerShape(12.dp)
    val cardCompact = RoundedCornerShape(8.dp)
    val badge = RoundedCornerShape(6.dp)
    val chip = RoundedCornerShape(50)
    val circular = RoundedCornerShape(50)
    val button = RoundedCornerShape(50)
}

// ── Card Sizes ──
object CardDefaults {
    val posterWidth = 160.dp
    val posterHeight = 240.dp
    val compactWidth = 180.dp
    val compactHeight = 270.dp
    val wideWidth = 320.dp
    val wideHeight = 180.dp
}

// ── Grid / Layout ──
object GridDefaults {
    val columns = 6
    val horizontalPadding = 56.dp
    val rowSpacing = 32.dp
    val columnSpacing = 24.dp
    val contentBottomPadding = 80.dp
}

// ── Hero Section ──
object HeroDefaults {
    val height = 340.dp
    val bottomFadeHeight = 160.dp
    val softFadeHeight = 320.dp
    val horizontalPadding = 56.dp
}

// ── Detail Screen ──
object DetailDefaults {
    val horizontalPadding = 64.dp
    val topPadding = 48.dp
    val posterWidth = 230.dp
    val posterHeight = 345.dp
}

// ── Focus Effects ──
object FocusDefaults {
    val borderWidth = 3.dp
    val borderWidthThick = 3.dp
    val cardScale = 1.1f
    val buttonScale = 1.1f
}

// ── Phone-specific sizes ──
object PhoneCardDefaults {
    val posterWidth = 120.dp
    val posterHeight = 180.dp
    val compactWidth = 140.dp
    val compactHeight = 210.dp
    val wideWidth = 240.dp
    val wideHeight = 135.dp
    val heroPosterWidth = 160.dp
    val heroPosterHeight = 240.dp
}

object PhoneGridDefaults {
    val columns = 3
    val horizontalPadding = 14.dp
    val rowSpacing = 14.dp
    val columnSpacing = 10.dp
    val contentBottomPadding = 24.dp
}

object PhoneHeroDefaults {
    val height = 260.dp
    val horizontalPadding = 14.dp
}
