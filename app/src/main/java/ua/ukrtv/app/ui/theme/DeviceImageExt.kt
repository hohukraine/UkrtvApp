package ua.ukrtv.app.ui.theme

import android.graphics.Bitmap
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import ua.ukrtv.app.util.DeviceClass

fun ImageRequest.Builder.deviceImage(
    deviceClass: DeviceClass,
    isMediatek: Boolean = false,
): ImageRequest.Builder = apply {
    bitmapConfig(Bitmap.Config.ARGB_8888)
    memoryCachePolicy(CachePolicy.ENABLED)
    diskCachePolicy(CachePolicy.ENABLED)
    crossfade(if (deviceClass != DeviceClass.LOW && !isMediatek) 100 else 0)
}
