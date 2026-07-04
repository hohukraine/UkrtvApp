package ua.ukrtv.app.player

import android.os.Build
import androidx.media3.common.Format
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodecHealthMonitor @Inject constructor() {

    data class DecoderStats(
        val name: String,
        val mimeType: String,
        var renderedFrames: Long = 0,
        var droppedFrames: Long = 0,
        var skippedFrames: Long = 0,
        var errors: Int = 0,
        var lastErrorTimeMs: Long = 0,
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
        stats.renderedFrames += count

        if (elapsedMs - lastCheckTimeMs > checkIntervalMs) {
            lastCheckTimeMs = elapsedMs
            val dropRate = if (stats.renderedFrames > 0) {
                stats.droppedFrames.toFloat() / stats.renderedFrames.toFloat()
            } else 0f

            if (dropRate > 0.05f && stats.renderedFrames > 100) {
                stats.isHealthy = false
                AppLogger.w("CodecHealth", "Decoder ${stats.name} unhealthy: dropRate=$dropRate")
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

    fun isMediatekDevice(): Boolean {
        val board = Build.BOARD.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()

        val knownMediatekBoards = listOf(
            "ikebukuro",        // MT5887/MT5889 (Changhong, etc.)
            "mt5887", "mt5889", "mt5867", "mt5879",
            "mt5890", "mt5891", "mt5893",
            "mt5590", "mt5592", "mt5593",
            "mt5658", "mt5660", "mt5661",
            "mtk5887", "mtk5889", "mtk5867",
        )

        return board.contains("mt") || board.contains("mediatek") ||
            board in knownMediatekBoards ||
            hardware.contains("mt") || hardware.contains("mediatek") ||
            brand.contains("mediatek") ||
            manufacturer.contains("mediatek") ||
            fingerprint.contains("mediatek") ||
            model.contains("mt") && model.any { it.isDigit() }
    }

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
            "Codec: ${decoder.name} | rendered=${decoder.renderedFrames} dropped=${decoder.droppedFrames} errors=${decoder.errors} healthy=${decoder.isHealthy}"
        } else {
            "Codec: not initialized"
        }
    }
}
