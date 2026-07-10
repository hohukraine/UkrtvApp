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
import java.util.concurrent.ConcurrentHashMap

object PosterColorCache {

    private val cache = ConcurrentHashMap<String, Color>()
    private const val MAX_SIZE = 200

    suspend fun getColor(context: Context, posterUrl: String, fallback: Color = Color(0xFF1A1A1A)): Color {
        cache[posterUrl]?.let { return it }
        val color = withContext(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(50, 75)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val palette = Palette.from(drawable.bitmap).generate()
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
        if (cache.size >= MAX_SIZE) cache.clear()
        cache[posterUrl] = color
        return color
    }

    suspend fun batchExtract(context: Context, urls: List<String>, fallback: Color = Color(0xFF1A1A1A)) {
        urls.forEach { url ->
            if (!cache.containsKey(url)) {
                getColor(context, url, fallback)
            }
        }
    }

    fun getCached(posterUrl: String): Color? = cache[posterUrl]

    fun contains(posterUrl: String): Boolean = cache.containsKey(posterUrl)

    fun clear() = cache.clear()
}
