package ua.ukrtv.app.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.media3.common.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AutoFrameRateHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isEnabled: Boolean = true
    private var surface: Surface? = null
    private var lastAppliedFps: Float = 0f
    private var targetFps: Float = 0f
    private var originalModeId: Int = -1

    private val mainHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    private val displayManager: DisplayManager?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        } else null

    private val defaultDisplay: Display?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isEnabled(): Boolean = isEnabled

    fun setSurface(surface: Surface?) {
        this.surface = surface
        if (surface == null) {
            lastAppliedFps = 0f
        }
    }

    fun onContentFrameRateDetected(frameRate: Float) {
        if (!isEnabled || frameRate <= 0f) return
        targetFps = matchFrameRate(frameRate)
        debounceApply()
    }

    fun onVideoFormatChanged(format: Format) {
        onContentFrameRateDetected(format.frameRate)
    }

    fun onPlaybackStarted() {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= 31) {
            applySurfaceFrameRate("play_start")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (targetFps > 0f) {
                setDisplayModeRate(targetFps)
            }
        }
        logEnvironment("play_start")
    }

    fun onPlaybackStopped() {
        clearAfr()
    }

    fun onSurfaceDestroyed() {
        lastAppliedFps = 0f
        targetFps = 0f
        surface = null
    }

    fun release() {
        clearAfr()
        surface = null
    }

    private fun debounceApply() {
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { applySurfaceFrameRate("debounce") }
        debounceRunnable = r
        mainHandler.postDelayed(r, DEBOUNCE_MS)
    }

    private fun applySurfaceFrameRate(reason: String) {
        if (Build.VERSION.SDK_INT < 31 || !isEnabled) return

        var fps = targetFps
        if (fps <= 0f) {
            fps = lastAppliedFps
            if (fps <= 0f) return
        }

        val matched = matchFrameRate(fps)

        if (lastAppliedFps > 0f && abs(lastAppliedFps - matched) < 0.2f) return

        val s = surface ?: return
        if (!s.isValid) return

        try {
            s.setFrameRate(matched, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ALWAYS)
            lastAppliedFps = matched
            Log.d(TAG, "AFR applied: reason=$reason fps=$matched")
        } catch (e: Exception) {
            AppLogger.e(TAG, "AFR setFrameRate failed: ${e.message}", e)
        }
    }

    private fun setDisplayModeRate(targetRate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = defaultDisplay ?: return
        val modes = display.supportedModes

        val targetMode = modes.minByOrNull { mode ->
            abs(mode.refreshRate - targetRate)
        } ?: return

        if (originalModeId < 0) {
            originalModeId = display.mode?.modeId ?: -1
        }

        try {
            val activity = context as? android.app.Activity
            activity?.window?.let { window ->
                val params = window.attributes
                params.preferredDisplayModeId = targetMode.modeId
                window.attributes = params
            }
            Log.d(TAG, "Display mode applied: mode=${targetMode.modeId}@${targetMode.refreshRate}Hz")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to set display mode: $targetRate", e)
        }
    }

    private fun clearAfr() {
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = null

        if (Build.VERSION.SDK_INT >= 31) {
            val s = surface
            if (s != null && s.isValid) {
                try {
                    s.setFrameRate(0f, 0, Surface.CHANGE_FRAME_RATE_ALWAYS)
                    Log.d(TAG, "AFR cleared")
                } catch (_: Exception) { }
            }
            lastAppliedFps = 0f
            targetFps = 0f
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (originalModeId >= 0) {
                try {
                    val activity = context as? android.app.Activity
                    activity?.window?.let { window ->
                        val params = window.attributes
                        params.preferredDisplayModeId = originalModeId
                        window.attributes = params
                    }
                    originalModeId = -1
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to restore display mode", e)
                }
            }
        }
    }

    fun matchFrameRate(contentFps: Float): Float {
        return when {
            abs(contentFps - 23.976f) < 0.2f -> 23.976f
            abs(contentFps - 24.0f)   < 0.2f -> 24.0f
            abs(contentFps - 25.0f)   < 0.2f -> 25.0f
            abs(contentFps - 29.97f)  < 0.2f -> 29.97f
            abs(contentFps - 30.0f)   < 0.2f -> 30.0f
            abs(contentFps - 50.0f)   < 0.3f -> 50.0f
            abs(contentFps - 59.94f)  < 0.3f -> 59.94f
            abs(contentFps - 60.0f)   < 0.3f -> 60.0f
            else -> contentFps
        }
    }

    fun getCurrentRefreshRate(): Float = defaultDisplay?.refreshRate ?: 60f

    fun logEnvironment(tag: String) {
        if (Build.VERSION.SDK_INT < 31) return

        val dm = displayManager
        val matchPreference = if (dm != null && Build.VERSION.SDK_INT >= 31) {
            dm.getMatchContentFrameRateUserPreference()
        } else -1

        val prefStr = when (matchPreference) {
            -1 -> "UNKNOWN"
            0 -> "NEVER"
            1 -> "SEAMLESS_ONLY"
            2 -> "ALWAYS"
            else -> "UNKNOWN_VALUE_$matchPreference"
        }

        val display = defaultDisplay
        val mode = display?.mode
        val supportedModes = display?.supportedModes

        val surfaceStr = if (surface != null) "Surface valid=${surface!!.isValid}" else "null"
        val modeStr = if (mode != null) "id=${mode.modeId} ${mode.physicalWidth}x${mode.physicalHeight}@${mode.refreshRate}Hz" else "null"
        val supportedStr = supportedModes?.joinToString(", ") {
            "${it.modeId}: ${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}Hz"
        } ?: "null"

        Log.d(TAG, "AFR env [$tag]: sdk=${Build.VERSION.SDK_INT}, " +
                "enabled=$isEnabled, matchPreference=$prefStr, " +
                "surface=$surfaceStr, activeMode=$modeStr, " +
                "supportedModes=[$supportedStr], " +
                "targetFps=$targetFps, lastApplied=$lastAppliedFps")
    }

    companion object {
        private const val TAG = "AutoFrameRate"
        private const val DEBOUNCE_MS = 300L
    }
}
