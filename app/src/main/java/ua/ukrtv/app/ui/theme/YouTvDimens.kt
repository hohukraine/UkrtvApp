package ua.ukrtv.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.ukrtv.app.R

// Montserrat (SIL OFL) — the typeface YouTV uses across TV screens.
val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_regular, FontWeight.Medium),
    Font(R.font.montserrat_semi_bold, FontWeight.SemiBold),
    Font(R.font.montserrat_semi_bold, FontWeight.Bold),
    Font(R.font.montserrat_semi_bold, FontWeight.Black)
)

/**
 * Metrics measured from the decompiled YouTV APK (res/values/dimens.xml + layouts).
 */
object YouTv {
    // Left page margin used by every screen (epg_start_line).
    val startLine: Dp = 42.dp

    // Row layout (MainVerticalGrid / card_video.xml).
    val gridClosedH: Dp = 52.dp
    val videoRowH: Dp = 242.dp          // MainVerticalGrid.V1(VIDEO)
    val collectionRowH: Dp = 224.dp     // V1(COLLECTION)
    val rowSpacing: Dp = 26.dp          // channel_card_spacing
    val cardRadius: Dp = 12.dp          // channel_card_corner_radius

    // VOD card (card_video.xml): poster 120x164 + title/genre lines under it.
    val vodCardW: Dp = 120.dp
    val vodCardH: Dp = 164.dp

    // Landscape "collection" cards used for trends rows.
    val collectionCardW: Dp = 266.dp
    val collectionCardH: Dp = 148.dp

    // Buttons (bg_button_selector): dark pill -> brand fill on focus.
    val buttonRadius: Dp = 18.dp        // tab_radius
    val buttonH: Dp = 54.dp             // drawer_item_height_big
    val playBtnW: Dp = 210.dp           // content_card_main_row play width
    val heroBtnW: Dp = 216.dp           // widget_content_info watch width
    val heroBtnH: Dp = 56.dp
    val bookmarkW: Dp = 64.dp

    // Hero overlay (widget_content_info.xml guidelines).
    const val HERO_INFO_TOP_PERCENT = 0.16f   // ci_gl_hor_18
    const val HERO_BTN_TOP_PERCENT = 0.66f    // ci_gl_hor_watch
    const val HERO_BTN_BIAS = 0.8f

    // Detail page bottom-anchored offsets (activity_video_detail.xml).
    val detailTitleBottom: Dp = 224.dp  // WidgetVideoDescription marginBottom
    val detailActionsBottom: Dp = 116.dp // ContentCardMainRow marginBottom
    val seasonsChipH: Dp = 32.dp
    val recommendedCollapsedH: Dp = 48.dp
    const val DESCRIPTION_GUIDELINE_PERCENT = 0.7f
}
