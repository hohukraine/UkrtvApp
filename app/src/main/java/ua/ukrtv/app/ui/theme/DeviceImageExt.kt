package ua.ukrtv.app.ui.theme

import android.graphics.Bitmap
import coil.request.CachePolicy
import coil.request.ImageRequest
import ua.ukrtv.app.util.DeviceClass

fun ImageRequest.Builder.deviceImage(
    deviceClass: DeviceClass,
    isMediatek: Boolean = false,
): ImageRequest.Builder = apply {
    bitmapConfig(Bitmap.Config.RGB_565)
    memoryCachePolicy(CachePolicy.ENABLED)
    diskCachePolicy(CachePolicy.ENABLED)
    crossfade(deviceClass != DeviceClass.LOW && !isMediatek)
}
