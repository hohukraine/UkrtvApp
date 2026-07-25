package ua.ukrtv.app.domain.model

data class ProgressData(
    val contentId: String,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val title: String = "",
    val poster: String = "",
    val pageUrl: String = "",
    val streamUrl: String? = null,
    val streamType: String? = null,
    val referer: String? = null,
    val fallbackUrls: String? = null,
    val seasonsJson: String? = null
)
