package ua.ukrtv.app.data.streaming

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedStreamProvider @Inject constructor(
    private val client: OkHttpClient
) {
    private val tag = "UnifiedStreamProvider"

    /**
     * Extracts HLS token and builds .m3u8 URL for ashdi.vip CDN.
     * Pattern: https://ashdi.vip/video15/2/new/[SLUG]_[ID]/hls/[TOKEN]/index.m3u8
     */
    suspend fun getStreamUrl(videoID: String, slug: String = "video"): String? = withContext(Dispatchers.IO) {
        try {
            val iframeUrl = "https://ashdi.vip/vod/$videoID"
            AppLogger.d(tag, "Resolving stream for videoID=$videoID via $iframeUrl")
            
            val request = Request.Builder()
                .url(iframeUrl)
                .header("Referer", "https://uakino.best/")
                .build()
                
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null
            
            // Extract token using regex as requested (no Jsoup/DOM)
            // Example token: BKiPlHaPmPtemhH+BIw=
            val tokenRegex = Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""")
            val token = tokenRegex.find(html)?.groupValues?.get(1)
            
            if (token != null) {
                if (token.startsWith("http")) {
                    AppLogger.d(tag, "Found direct URL in iframe: $token")
                    return@withContext token
                }
                
                val finalUrl = "https://ashdi.vip/video15/2/new/${slug}_$videoID/hls/$token/index.m3u8"
                AppLogger.d(tag, "Constructed HLS URL: $finalUrl")
                return@withContext finalUrl
            }
            
            AppLogger.w(tag, "Could not find token in iframe HTML")
            null
        } catch (e: Exception) {
            AppLogger.e(tag, "Error in UnifiedStreamProvider for ID $videoID", e)
            null
        }
    }
}
