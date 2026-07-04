package ua.ukrtv.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchProgress(
    val contentId: String,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long = 0,
    val title: String = "",
    val poster: String = "",
    val pageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val progressPercentage: Int get() = if (durationMs > 0) {
        ((positionMs.toDouble() / durationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
    } else 0
}
