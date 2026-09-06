package ua.ukrtv.app.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Deterministic focus restore: retries [requestFocus][FocusRequester.requestFocus] across
 * composition frames until it succeeds or [maxAttempts] is exhausted.
 *
 * Returns `true` if focus was granted, `false` if all attempts failed.
 */
suspend fun FocusRequester.restoreFocus(
    maxAttempts: Int = 15,
    initialDelayFrames: Int = 1
): Boolean {
    repeat(initialDelayFrames) { withFrameNanos {} }
    for (attempt in 0 until maxAttempts) {
        withFrameNanos {}
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
    }
    return false
}

/**
 * Play a short navigation click sound, throttled to at most once per [throttleMs].
 */
fun playNavigationSound(context: Context, throttleMs: Long = 150L, lastTimeHolder: MutableLongStateHolder) {
    val now = System.currentTimeMillis()
    if (now - lastTimeHolder.value > throttleMs) {
        lastTimeHolder.value = now
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
    }
}

/** Mutable holder for a Long timestamp, used by [playNavigationSound]. */
class MutableLongStateHolder(initial: Long = 0L) {
    var value: Long = initial
}

/**
 * Shared composable that requests focus on [requester] after [delayMs] once
 * [shouldRequest] becomes true. Uses deterministic frame-based retry.
 */
@Composable
fun AutoFocusRequester(
    shouldRequest: Boolean,
    requester: FocusRequester,
    delayMs: Long = 0,
    maxAttempts: Int = 15
) {
    LaunchedEffect(shouldRequest) {
        if (!shouldRequest) return@LaunchedEffect
        if (delayMs > 0) delay(delayMs)
        requester.restoreFocus(maxAttempts)
    }
}
