package ua.ukrtv.app.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
@Immutable
@Serializable
data class WatchProgress(
    val contentId: String,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long = 0,
    val title: String = "",
    val poster: String = "",
    val pageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val streamUrl: String? = null,
    val streamType: String? = null,
    val referer: String? = null
) {
    val progressPercentage: Int get() = if (durationMs > 0) {
        ((positionMs.toDouble() / durationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
    } else 0
}
