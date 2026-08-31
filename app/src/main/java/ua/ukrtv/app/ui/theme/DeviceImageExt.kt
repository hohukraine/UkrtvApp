package ua.ukrtv.app.ui.theme

import android.graphics.Bitmap
import android.os.Build
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import ua.ukrtv.app.util.DeviceClass

fun ImageRequest.Builder.deviceImage(
    deviceClass: DeviceClass,
    isMediatek: Boolean = false,
): ImageRequest.Builder = apply {
    // HIGH devices (API 28+) use hardware bitmaps to save ~50% memory
    if (deviceClass == DeviceClass.HIGH && Build.VERSION.SDK_INT >= 28 && !isMediatek) {
        bitmapConfig(Bitmap.Config.HARDWARE)
    } else {
        bitmapConfig(Bitmap.Config.ARGB_8888)
    }
    memoryCachePolicy(CachePolicy.ENABLED)
    diskCachePolicy(CachePolicy.ENABLED)
    crossfade(if (deviceClass == DeviceClass.HIGH && !isMediatek) 100 else 0)
}
