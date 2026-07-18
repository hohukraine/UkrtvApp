package ua.ukrtv.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object PosterColorCache {

    private const val MAX_SIZE = 150
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Color>(MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean {
                return size > MAX_SIZE
            }
        }
    )

    suspend fun getColor(context: Context, posterUrl: String, fallback: Color = Color(0xFF1A1A1A)): Color {
        cache[posterUrl]?.let { return it }
        val color = withContext(Dispatchers.Default) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(50, 75)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .allowHardware(false) // Required for Palette
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val palette = Palette.from(drawable.bitmap)
                            .maximumColorCount(8) // Performance: few colors for UI accents
                            .generate()
                        val swatch = palette.vibrantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.darkMutedSwatch
                        if (swatch != null) return@withContext Color(swatch.rgb)
                    }
                }
            } catch (_: Exception) { }
            fallback
        }
        cache[posterUrl] = color
        return color
    }

    fun getCached(posterUrl: String): Color? = cache[posterUrl]
}
