package ua.ukrtv.app.player

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val THERMAL_STATUS_NONE = PowerManager.THERMAL_STATUS_NONE
        const val THERMAL_STATUS_LIGHT = PowerManager.THERMAL_STATUS_LIGHT
        const val THERMAL_STATUS_MODERATE = PowerManager.THERMAL_STATUS_MODERATE
        const val THERMAL_STATUS_SEVERE = PowerManager.THERMAL_STATUS_SEVERE
        const val THERMAL_STATUS_CRITICAL = PowerManager.THERMAL_STATUS_CRITICAL
        const val THERMAL_STATUS_EMERGENCY = PowerManager.THERMAL_STATUS_EMERGENCY
        const val THERMAL_STATUS_SHUTDOWN = PowerManager.THERMAL_STATUS_SHUTDOWN
    }

    val thermalStatus: Flow<Int> = callbackFlow {
        trySend(THERMAL_STATUS_NONE)

        if (Build.VERSION.SDK_INT < 31) {
            awaitClose()
            return@callbackFlow
        }

        val powerManager = context.getSystemService<PowerManager>()
        if (powerManager == null) {
            AppLogger.w("ThermalMonitor", "PowerManager not available")
            awaitClose()
            return@callbackFlow
        }

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            AppLogger.d("ThermalMonitor", "Thermal status changed: $status")
            trySend(status)
        }

        powerManager.addThermalStatusListener(listener)

        awaitClose {
            try {
                powerManager.removeThermalStatusListener(listener)
            } catch (_: Exception) {}
        }
    }

    fun isThrottling(status: Int): Boolean = status >= THERMAL_STATUS_SEVERE

    fun getQualityLevel(status: Int): QualityLevel = when {
        status >= THERMAL_STATUS_CRITICAL -> QualityLevel.MINIMAL
        status >= THERMAL_STATUS_SEVERE -> QualityLevel.LOW
        status >= THERMAL_STATUS_MODERATE -> QualityLevel.MEDIUM
        else -> QualityLevel.HIGH
    }

    enum class QualityLevel {
        HIGH,
        MEDIUM,
        LOW,
        MINIMAL
    }
}
