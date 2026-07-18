package ua.ukrtv.app.player

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import ua.ukrtv.app.util.AppLogger

object DeviceFingerprint {

    data class DeviceInfo(
        val device: String,
        val model: String,
        val manufacturer: String,
        val board: String,
        val hardware: String,
        val brand: String,
        val apiLevel: Int,
        val releaseVersion: String,
        val fingerprint: String,
        val supportedAbis: List<String>,
        val isMediatek: Boolean,
        val deviceClass: String
    )

    private var cachedInfo: DeviceInfo? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getDeviceInfo(): DeviceInfo {
        cachedInfo?.let { return it }

        val info = DeviceInfo(
            device = Build.DEVICE,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            brand = Build.BRAND,
            apiLevel = Build.VERSION.SDK_INT,
            releaseVersion = Build.VERSION.RELEASE,
            fingerprint = Build.FINGERPRINT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            isMediatek = detectMediatek(),
            deviceClass = detectDeviceClass()
        )

        cachedInfo = info
        logDeviceInfo(info)
        return info
    }

    private fun detectMediatek(): Boolean {
        val board = Build.BOARD.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()

        val knownMediatekBoards = listOf(
            "ikebukuro",
            "mt5887", "mt5889", "mt5867", "mt5879",
            "mt5890", "mt5891", "mt5893",
            "mt5590", "mt5592", "mt5593",
            "mt5658", "mt5660", "mt5661",
            "mtk5887", "mtk5889", "mtk5867",
        )

        return board in knownMediatekBoards ||
            board.contains("mediatek") ||
            board.startsWith("mt") ||
            hardware.contains("mediatek") ||
            hardware.startsWith("mt") ||
            manufacturer.contains("mediatek") ||
            fingerprint.contains("mediatek") ||
            (model.contains("mt") && model.any { it.isDigit() })
    }

    private fun detectDeviceClass(): String {
        val context = appContext ?: return "unknown"
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "unknown"
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            am.isLowRamDevice || totalRamGb <= 1.0 -> "low"
            totalRamGb <= 2.0 || cores <= 4 -> "mid"
            else -> "high"
        }
    }

    private fun logDeviceInfo(info: DeviceInfo) {
        AppLogger.i("DeviceFingerprint", "=== Device Information ===")
        AppLogger.i("DeviceFingerprint", "Device: ${info.device}")
        AppLogger.i("DeviceFingerprint", "Model: ${info.model}")
        AppLogger.i("DeviceFingerprint", "Manufacturer: ${info.manufacturer}")
        AppLogger.i("DeviceFingerprint", "Board: ${info.board}")
        AppLogger.i("DeviceFingerprint", "Hardware: ${info.hardware}")
        AppLogger.i("DeviceFingerprint", "Brand: ${info.brand}")
        AppLogger.i("DeviceFingerprint", "API Level: ${info.apiLevel}")
        AppLogger.i("DeviceFingerprint", "Release: ${info.releaseVersion}")
        AppLogger.i("DeviceFingerprint", "Fingerprint: ${info.fingerprint}")
        AppLogger.i("DeviceFingerprint", "Supported ABIs: ${info.supportedAbis}")
        AppLogger.i("DeviceFingerprint", "Mediatek: ${info.isMediatek}")
        AppLogger.i("DeviceFingerprint", "Device Class: ${info.deviceClass}")
        AppLogger.i("DeviceFingerprint", "========================")
    }
}
