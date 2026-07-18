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

    val knownMediatekBoards = listOf(
        "ikebukuro",
        "mt5887", "mt5889", "mt5867", "mt5879",
        "mt5890", "mt5891", "mt5893",
        "mt5590", "mt5592", "mt5593",
        "mt5658", "mt5660", "mt5661",
        "mtk5887", "mtk5889", "mtk5867",
    )

    return board in knownMediatekBoards ||
        board.contains("mt") || board.contains("mediatek") ||
        hardware.contains("mt") || hardware.contains("mediatek") ||
        brand.contains("mediatek") || manufacturer.contains("mediatek") ||
        fingerprint.contains("mediatek") ||
        (model.contains("mt") && model.any { it.isDigit() })
}


