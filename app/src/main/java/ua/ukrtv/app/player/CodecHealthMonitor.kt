package ua.ukrtv.app.player

import androidx.media3.common.Format
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodecHealthMonitor @Inject constructor() {

    data class DecoderStats(
        val name: String,
        val mimeType: String,
        var droppedFrames: Long = 0,
        var droppedFramesSinceLastCheck: Long = 0,
        var errors: Int = 0,
        var lastErrorTimeMs: Long = 0,
        var lastCheckTimeMs: Long = 0,
        var isHealthy: Boolean = true
    )

    private var currentDecoder: DecoderStats? = null
    private val decoderHistory = mutableMapOf<String, DecoderStats>()
    private var checkIntervalMs = 30_000L
    private var lastCheckTimeMs = 0L

    val hasHealthyDecoder: Boolean
        get() = currentDecoder?.isHealthy != false

    val currentDecoderName: String?
        get() = currentDecoder?.name

    fun onDecoderInitialized(decoderName: String, mimeType: String) {
        val stats = decoderHistory.getOrPut(decoderName) {
            DecoderStats(name = decoderName, mimeType = mimeType)
        }
        currentDecoder = stats
        AppLogger.d("CodecHealth", "Decoder initialized: $decoderName ($mimeType)")
    }

    fun onDroppedFrames(count: Int, elapsedMs: Long) {
        val stats = currentDecoder ?: return
        stats.droppedFrames += count
        stats.droppedFramesSinceLastCheck += count

        if (elapsedMs - stats.lastCheckTimeMs > checkIntervalMs) {
            val windowMs = (elapsedMs - stats.lastCheckTimeMs).coerceAtLeast(1)
            val droppedPerSecond = stats.droppedFramesSinceLastCheck * 1000.0 / windowMs
            stats.lastCheckTimeMs = elapsedMs
            stats.droppedFramesSinceLastCheck = 0

            if (droppedPerSecond > 30.0 && stats.droppedFrames > 100) {
                stats.isHealthy = false
                AppLogger.w("CodecHealth", "Decoder ${stats.name} unhealthy: ${"%.1f".format(droppedPerSecond)} dropped/s (total=${stats.droppedFrames})")
            }
        }
    }

    fun onDecoderError() {
        val stats = currentDecoder ?: return
        stats.errors++
        stats.lastErrorTimeMs = System.currentTimeMillis()
        if (stats.errors >= 2) {
            stats.isHealthy = false
            AppLogger.w("CodecHealth", "Decoder ${stats.name} marked unhealthy after ${stats.errors} errors")
        }
    }

    fun shouldExcludeDecoder(decoderName: String, format: Format? = null): Boolean {
        val name = decoderName.lowercase()

        // Mediatek HEVC decoder known to black-screen on resolution changes
        if (name.contains("mtk") && name.contains("hevc")) {
            if (format != null && format.width > 1920) return true
        }

        // Exclude MStar AVC decoder ONLY on non-Mediatek devices
        if (name.contains("omx.ms.") && name.contains("avc") && !isMediatekDevice()) {
            return true
        }

        return false
    }

    fun isMediatekDevice(): Boolean = ua.ukrtv.app.util.hasMediatekChipset()

    fun isKnownProblematicDecoder(decoderName: String): Boolean {
        val name = decoderName.lowercase()
        // Known problematic decoders on Android TV
        return name.contains("c2.mtk.hevc") ||
            name.contains("omx.ms.") ||
            name.contains("omx.mtk.") && name.contains("hevc")
    }

    override fun toString(): String {
        val decoder = currentDecoder
        return if (decoder != null) {
            "Codec: ${decoder.name} | dropped=${decoder.droppedFrames} errors=${decoder.errors} healthy=${decoder.isHealthy}"
        } else {
            "Codec: not initialized"
        }
    }
}
