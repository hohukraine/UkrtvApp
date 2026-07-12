package ua.ukrtv.app.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun LockScreenOrientation(orientation: Int) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(orientation) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose { activity.requestedOrientation = previous }
    }
}

fun Activity.applyPlayerOrientation(allowRotation: Boolean) {
    requestedOrientation = if (allowRotation) {
        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

fun Activity.togglePlayerRotation(currentLandscape: Boolean) {
    requestedOrientation = if (currentLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

fun Activity.setImmersive(immersive: Boolean) {
    val window = window ?: return
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (immersive) {
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
