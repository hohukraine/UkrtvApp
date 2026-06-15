package ua.ukrtv.app.domain.model

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

data class PlaybackStats(
    val deviceId: String,
    val contentId: String,
    val episodeId: String?,
    val avgFps: Float = 0f,
    val droppedFrames: Int = 0,
    val bufferUnderruns: Int = 0,
    val totalWatchTimeMs: Long = 0L,
    val formatCodec: String? = null,
    val formatHeight: Int = 0,
    val formatBitrate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isHealthy: Boolean get() = avgFps >= 24 && droppedFrames < 50 && bufferUnderruns < 10
}
