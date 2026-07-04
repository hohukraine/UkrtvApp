package ua.ukrtv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Spacing ──
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

// ── Shapes ──
object Shapes {
    val card = RoundedCornerShape(12.dp)
    val cardCompact = RoundedCornerShape(8.dp)
    val cardWide = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(10.dp)
    val badge = RoundedCornerShape(6.dp)
    val chip = RoundedCornerShape(50)
    val dialog = RoundedCornerShape(20.dp)
    val circular = RoundedCornerShape(50)
    val progressBar = RoundedCornerShape(4.dp)
}

// ── Card Sizes ──
object CardDefaults {
    val posterWidth = 160.dp
    val posterHeight = 240.dp
    val compactWidth = 180.dp
    val compactHeight = 270.dp
    val wideWidth = 320.dp
    val wideHeight = 180.dp
    val episodeWidth = 200.dp
    val episodeHeight = 112.dp
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
    val bottomFadeHeight = 80.dp
    val horizontalPadding = 56.dp
    val autoScrollMs = 6000L
}

// ── Detail Screen ──
object DetailDefaults {
    val horizontalPadding = 64.dp
    val topPadding = 48.dp
    val posterWidth = 230.dp
    val posterHeight = 345.dp
    val backdropAlpha = 0.5f
    val buttonHorizontalPadding = 40.dp
    val buttonVerticalPadding = 14.dp
}

// ── Focus Effects ──
object FocusDefaults {
    val borderWidth = 3.dp
    val borderWidthThick = 3.dp
    val cardScale = 1.1f
    val compactCardScale = 1.1f
    val buttonScale = 1.1f
}
