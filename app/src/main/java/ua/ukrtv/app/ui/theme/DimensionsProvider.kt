package ua.ukrtv.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PosterStyle { 
    WIDE, VERTICAL;
    
    companion object {
        fun forProvider(provider: String?): PosterStyle =
            if (provider?.uppercase()?.contains("UAFLIX") == true) WIDE else VERTICAL
    }
}

data class CardDimensions(
    val width: Dp,
    val height: Dp
)

object ProviderSizes {
    fun card(style: PosterStyle): CardDimensions = when (style) {
        PosterStyle.WIDE -> CardDimensions(CardDefaults.wideWidth, CardDefaults.wideHeight)
        PosterStyle.VERTICAL -> CardDimensions(CardDefaults.posterWidth, CardDefaults.posterHeight)
    }

    fun compactCard(style: PosterStyle): CardDimensions = when (style) {
        PosterStyle.WIDE -> CardDimensions(240.dp, 135.dp) // wide compact
        PosterStyle.VERTICAL -> CardDimensions(CardDefaults.compactWidth, CardDefaults.compactHeight)
    }

    fun phoneCard(style: PosterStyle): CardDimensions = when (style) {
        PosterStyle.WIDE -> CardDimensions(PhoneCardDefaults.wideWidth, PhoneCardDefaults.wideHeight)
        PosterStyle.VERTICAL -> CardDimensions(PhoneCardDefaults.posterWidth, PhoneCardDefaults.posterHeight)
    }

    fun detailPoster(style: PosterStyle): CardDimensions = when (style) {
        PosterStyle.WIDE -> CardDimensions(DetailDefaults.wideWidth, DetailDefaults.wideHeight)
        PosterStyle.VERTICAL -> CardDimensions(DetailDefaults.posterWidth, DetailDefaults.posterHeight)
    }

    // TV home shelf rows keep a constant height (matching the current VOD row) so every
    // section lines up; only the width adapts to the provider's poster aspect.
    fun tvRowCard(style: PosterStyle): CardDimensions = when (style) {
        PosterStyle.WIDE -> CardDimensions(YouTv.collectionCardW, YouTv.vodCardH)
        PosterStyle.VERTICAL -> CardDimensions(YouTv.vodCardW, YouTv.vodCardH)
    }

    // "Continue watching" is always presented as a uniform 16:9 landscape tile regardless of
    // provider, so UaFix wide posters fit fully and Uakino posters center-crop consistently.
    fun tvContinueCard(): CardDimensions = CardDimensions(YouTv.collectionCardW, YouTv.vodCardH)
}
