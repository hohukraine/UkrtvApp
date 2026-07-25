package ua.ukrtv.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

object PosterColorCache {

    private const val MAX_SIZE = 150
    private const val MAX_CONCURRENT_EXTRACTIONS = 3
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Color>(MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean {
                return size > MAX_SIZE
            }
        }
    )
    private val semaphore = Semaphore(MAX_CONCURRENT_EXTRACTIONS)

    suspend fun getColor(context: Context, posterUrl: String, fallback: Color = Color(0xFF1A1A1A)): Color {
        cache[posterUrl]?.let { return it }
        val color = semaphore.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(posterUrl)
                        .size(50, 75)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    var extractedColor: Color? = null
                    if (result is SuccessResult) {
                        val drawable = result.image.asDrawable(context.resources)
                        if (drawable is BitmapDrawable) {
                            val bitmap = drawable.bitmap
                            val palette = Palette.from(bitmap)
                                .maximumColorCount(6)
                                .generate()
                            val swatch = palette.vibrantSwatch
                                ?: palette.mutedSwatch
                                ?: palette.darkVibrantSwatch
                                ?: palette.darkMutedSwatch
                            if (swatch != null) extractedColor = Color(swatch.rgb)
                        }
                    }
                    extractedColor ?: fallback
                } catch (_: Exception) {
                    fallback
                }
            }
        }
        cache[posterUrl] = color
        return color
    }

    fun getCached(posterUrl: String): Color? = cache[posterUrl]
}
