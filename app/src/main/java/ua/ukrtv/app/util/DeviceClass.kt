package ua.ukrtv.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class DeviceClass {
    LOW, MID, HIGH
}

@Volatile
private var cachedDeviceClass: DeviceClass? = null

fun getDeviceClass(context: Context): DeviceClass {
    cachedDeviceClass?.let { return it }
    
    synchronized(DeviceClass::class.java) {
        cachedDeviceClass?.let { return it }
        
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return DeviceClass.MID
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val density = context.resources.displayMetrics.densityDpi

        if (am.isLowRamDevice || totalRamGb <= 1.0) return DeviceClass.LOW.also { cachedDeviceClass = it }

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

        val result = when {
            score <= 1 -> DeviceClass.LOW
            score <= 3 -> DeviceClass.MID
            else -> DeviceClass.HIGH
        }
        cachedDeviceClass = result
        return result
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

fun DeviceClass.maxPostersPerRow(): Int = when (this) {
    DeviceClass.LOW -> 8
    DeviceClass.MID -> 12
    DeviceClass.HIGH -> 16
}

fun DeviceClass.maxShimmerItems(): Int = when (this) {
    DeviceClass.LOW -> 4
    DeviceClass.MID -> 6
    DeviceClass.HIGH -> 8
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
                maxVideoBitrate = 25_000_000,
                maxVideoSize = 1920,
            )
            DeviceClass.HIGH -> BufferParams(
                minBufferMs = 90_000,
                maxBufferMs = 300_000,
                bufferForPlaybackMs = 1_500,
                bufferForPlaybackAfterRebufferMs = 4_000,
                maxVideoBitrate = 50_000_000,
                maxVideoSize = 3840,
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

@Volatile
private var cachedMediatek: Boolean? = null

fun hasMediatekChipset(): Boolean {
    cachedMediatek?.let { return it }

    val board = Build.BOARD.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val model = Build.MODEL.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    val brand = Build.BRAND.lowercase()
    val fingerprint = Build.FINGERPRINT.lowercase()

    val knownMediatekBoards = listOf(
        "ikebukuro",
        "mt5887", "mt5889", "mt5867", "mt5879",
        "mt5890", "mt5891", "mt5893",
        "mt5590", "mt5592", "mt5593",
        "mt5658", "mt5660", "mt5661",
        "mtk5887", "mtk5889", "mtk5867",
    )

    val result = board in knownMediatekBoards ||
        board.contains("mt") || board.contains("mediatek") ||
        hardware.contains("mt") || hardware.contains("mediatek") ||
        brand.contains("mediatek") || manufacturer.contains("mediatek") ||
        fingerprint.contains("mediatek") ||
        (model.contains("mt") && model.any { it.isDigit() })

    cachedMediatek = result
    return result
}
