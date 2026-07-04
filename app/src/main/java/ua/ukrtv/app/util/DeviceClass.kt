package ua.ukrtv.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class DeviceClass {
    LOW, MID, HIGH
}

fun getDeviceClass(context: Context): DeviceClass {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return DeviceClass.MID
    val memClass = am.memoryClass
    val isLowRam = am.isLowRamDevice
    return when {
        memClass <= 96 || isLowRam -> DeviceClass.LOW
        memClass <= 256 -> DeviceClass.MID
        else -> DeviceClass.HIGH
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
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 5_000,
                maxVideoBitrate = 10_000_000,
                maxVideoSize = 1280,
            )
            DeviceClass.MID -> BufferParams(
                minBufferMs = 50_000,
                maxBufferMs = 100_000,
                bufferForPlaybackMs = 2_000,
                bufferForPlaybackAfterRebufferMs = 4_000,
                maxVideoBitrate = 15_000_000,
                maxVideoSize = 1920,
            )
            DeviceClass.HIGH -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 150_000,
                bufferForPlaybackMs = 1_500,
                bufferForPlaybackAfterRebufferMs = 3_000,
                maxVideoBitrate = 20_000_000,
                maxVideoSize = 1920,
            )
        }

        if (isMediatek) {
            return base.copy(
                minBufferMs = base.minBufferMs.coerceAtLeast(35_000),
                bufferForPlaybackAfterRebufferMs = base.bufferForPlaybackAfterRebufferMs.coerceAtLeast(4_000),
            )
        }

        return base
    }
}
