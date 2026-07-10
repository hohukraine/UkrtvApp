package ua.ukrtv.app.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import ua.ukrtv.app.util.AppLogger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.LinkedHashMap

class WebpToJpegInterceptor : Interceptor {
    companion object {
        private val WEBP_MAGIC = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        private val WEBP_TYPE = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
        private val SKIP_PATTERNS = listOf(
            ".ts", ".m3u8", ".mpd", ".m4s",
            "/segment", "/hls/", "/dash/",
            "/video", "/media",
            "ashdi.vip/video", "hdvbua.pro/media"
        )
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
        private data class CacheEntry(val bytes: ByteArray, val timestamp: Long)
        private const val CACHE_TTL_MS = 3_600_000L
        private val JPEG_CACHE = object : LinkedHashMap<String, CacheEntry>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>): Boolean = size > 100
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString().lowercase()

        for (pattern in SKIP_PATTERNS) {
            if (url.contains(pattern)) return chain.proceed(request)
        }

        val isLikelyImage = IMAGE_EXTENSIONS.any { url.contains(it) } ||
            request.header("Accept")?.contains("image") == true

        val modifiedRequest = if (isLikelyImage) {
            request.newBuilder()
                .header("Accept", "image/jpeg,image/png,image/webp;q=0.8")
                .build()
        } else {
            request
        }

        val response = chain.proceed(modifiedRequest)
        val body = response.body ?: return response

        val contentType = body.contentType()
        val contentTypeString = contentType?.toString()?.lowercase() ?: ""
        
        // If it's already JPEG, skip conversion
        if (contentTypeString.contains("jpeg")) return response
        
        // If it's not likely to be an image based on content type AND not an image extension, skip
        if (!contentTypeString.contains("image") && !contentTypeString.contains("octet-stream") && 
            IMAGE_EXTENSIONS.none { url.contains(it) }) {
            return response
        }

        synchronized(JPEG_CACHE) {
            JPEG_CACHE[url]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    body.close()
                    return response.newBuilder()
                        .removeHeader("Content-Length")
                        .body(entry.bytes.toResponseBody("image/jpeg".toMediaType()))
                        .build()
                }
                JPEG_CACHE.remove(url)
            }
        }

        val bytes = try {
            body.use { it.bytes() }
        } catch (e: Exception) {
            AppLogger.w("WebpToJpeg", "Failed to read body for $url: ${e.message}")
            return response
        }

        if (bytes.size < 12) {
            return response.newBuilder().body(bytes.toResponseBody(contentType)).build()
        }

        val isWebp = bytes[0] == WEBP_MAGIC[0] && bytes[1] == WEBP_MAGIC[1] && 
                     bytes[2] == WEBP_MAGIC[2] && bytes[3] == WEBP_MAGIC[3] &&
                     bytes[8] == WEBP_TYPE[0] && bytes[9] == WEBP_TYPE[1] && 
                     bytes[10] == WEBP_TYPE[2] && bytes[11] == WEBP_TYPE[3]

        if (!isWebp) {
            return response.newBuilder().body(bytes.toResponseBody(contentType)).build()
        }

        return convertAndReplace(bytes, url, response, contentType)
    }

    private fun convertAndReplace(bytes: ByteArray, url: String, response: Response, originalContentType: okhttp3.MediaType?): Response {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {}
        
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        if (options.outWidth > 1000 || options.outHeight > 1000) {
            options.inSampleSize = 2
        }

        var bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        }

        if (bitmap == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (options.inSampleSize > 1) {
                        decoder.setTargetSampleSize(options.inSampleSize)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("WebpToJpeg", "WebP ImageDecoder failed for $url: ${e.message}")
            }
        }

        if (bitmap == null) {
            // Last ditch attempt with default settings
            bitmap = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
        }

        if (bitmap == null) {
            AppLogger.w("WebpToJpeg", "All decoders failed for WebP: $url")
            return response.newBuilder().body(bytes.toResponseBody(originalContentType)).build()
        }

        val output = ByteArrayOutputStream()
        val success = try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
        } catch (e: Exception) {
            AppLogger.w("WebpToJpeg", "JPEG compress failed for $url: ${e.message}")
            false
        } finally {
            bitmap.recycle()
        }

        if (!success) {
            return response.newBuilder().body(bytes.toResponseBody(originalContentType)).build()
        }

        val jpegBytes = output.toByteArray()
        synchronized(JPEG_CACHE) { JPEG_CACHE[url] = CacheEntry(jpegBytes, System.currentTimeMillis()) }
        
        AppLogger.d("WebpToJpeg", "Converted ${bytes.size}B WebP -> ${jpegBytes.size}B JPEG for $url")

        return response.newBuilder()
            .removeHeader("Content-Length")
            .body(jpegBytes.toResponseBody("image/jpeg".toMediaType()))
            .build()
    }
}
