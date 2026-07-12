package ua.ukrtv.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class DeviceClass {
    LOW, MID, HIGH
}

fun getDeviceClass(context: Context): DeviceClass {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return DeviceClass.MID
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    val cores = Runtime.getRuntime().availableProcessors()
    val density = context.resources.displayMetrics.densityDpi

    if (am.isLowRamDevice || totalRamGb <= 1.0) return DeviceClass.LOW

    var score = 0
    score += when {
        totalRamGb >= 3.5 -> 2
        totalRamGb >= 2.0 -> 1
        else -> 0
    }
    score += when {
        cores >= 8 -> 2
        cores >= 4 -> 1
        else -> 0
    }
    // High-density screens cost more GPU bandwidth to composite
    score += when {
        density <= 320 -> 2
        density <= 420 -> 1
        else -> 0
    }

    return when {
        score <= 1 -> DeviceClass.LOW
        score <= 3 -> DeviceClass.MID
        else -> DeviceClass.HIGH
    }
}

fun resolveDeviceClass(context: Context, profile: PerformanceProfile): DeviceClass {
    val hardware = getDeviceClass(context)
    return when (profile) {
        PerformanceProfile.AUTO -> hardware
        PerformanceProfile.PERFORMANCE -> DeviceClass.LOW
        PerformanceProfile.BALANCED -> DeviceClass.MID
        PerformanceProfile.VISUAL -> DeviceClass.HIGH
    }
}

fun hasMediatekChipset(): Boolean {
    val board = Build.BOARD.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val model = Build.MODEL.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    val brand = Build.BRAND.lowercase()
    val fingerprint = Build.FINGERPRINT.lowercase()

    return board.contains("mt") || board.contains("mediatek") ||
        hardware.contains("mt") || hardware.contains("mediatek") ||
        brand.contains("mediatek") || manufacturer.contains("mediatek") ||
        fingerprint.contains("mediatek") ||
        (model.contains("mt") && model.any { it.isDigit() })
}

object PlayerBufferConfig {
    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int,
        val maxVideoBitrate: Int,
        val maxVideoSize: Int,
    )

    fun forDevice(deviceClass: DeviceClass, isMediatek: Boolean): BufferParams {
        val base = when (deviceClass) {
            DeviceClass.LOW -> BufferParams(
                minBufferMs = 45_000,
                maxBufferMs = 120_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 6_000,
                maxVideoBitrate = 10_000_000,
                maxVideoSize = 1280,
            )
            DeviceClass.MID -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 180_000,
                bufferForPlaybackMs = 2_000,
                bufferForPlaybackAfterRebufferMs = 5_000,
                maxVideoBitrate = 15_000_000,
                maxVideoSize = 1920,
            )
            DeviceClass.HIGH -> BufferParams(
                minBufferMs = 90_000,
                maxBufferMs = 300_000,
                bufferForPlaybackMs = 1_500,
                bufferForPlaybackAfterRebufferMs = 4_000,
                maxVideoBitrate = 20_000_000,
                maxVideoSize = 1920,
            )
        }

        if (isMediatek) {
            return base.copy(
                minBufferMs = base.minBufferMs.coerceAtLeast(45_000),
                bufferForPlaybackAfterRebufferMs = base.bufferForPlaybackAfterRebufferMs.coerceAtLeast(6_000),
            )
        }

        return base
    }
}
