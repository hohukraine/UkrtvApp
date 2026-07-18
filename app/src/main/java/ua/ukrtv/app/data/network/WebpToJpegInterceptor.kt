package ua.ukrtv.app.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class WebpToJpegInterceptor : Interceptor {
    companion object {
        private val WEBP_MAGIC = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        private val WEBP_TYPE = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
        private val SKIP_PATTERNS = listOf(
            ".ts", ".m3u8", ".mpd", ".m4s",
            "/segment", "/hls/", "/dash/",
            "/video", "/media"
        )
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif")
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
                .header("Accept", "image/avif,image/jpeg,image/png,*/*;q=0.5")
                .build()
        } else {
            request
        }

        val response = chain.proceed(modifiedRequest)
        val body = response.body ?: return response
        val contentType = body.contentType()
        val contentTypeString = contentType?.toString()?.lowercase() ?: ""

        if (!contentTypeString.contains("image/webp")) return response

        val bytes = try {
            body.bytes()
        } catch (_: Exception) {
            return response.newBuilder()
                .code(502)
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val isWebp = bytes.size >= 12 && 
                     bytes[0] == WEBP_MAGIC[0] && bytes[1] == WEBP_MAGIC[1] && 
                     bytes[2] == WEBP_MAGIC[2] && bytes[3] == WEBP_MAGIC[3] &&
                     bytes[8] == WEBP_TYPE[0] && bytes[9] == WEBP_TYPE[1] && 
                     bytes[10] == WEBP_TYPE[2] && bytes[11] == WEBP_TYPE[3]

        if (!isWebp) {
            return response.newBuilder().body(bytes.toResponseBody(contentType)).build()
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return response.newBuilder().body(bytes.toResponseBody(contentType)).build()
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        bitmap.recycle()

        val jpegBytes = output.toByteArray()
        return response.newBuilder()
            .removeHeader("Content-Length")
            .header("Content-Type", "image/jpeg")
            .body(jpegBytes.toResponseBody("image/jpeg".toMediaType()))
            .build()
    }
}
