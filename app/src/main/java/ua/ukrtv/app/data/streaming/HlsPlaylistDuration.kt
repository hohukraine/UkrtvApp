package ua.ukrtv.app.data.streaming

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.Constants
import ua.ukrtv.app.util.AppLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HlsPlaylistDuration @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    data class Variant(
        val url: String,
        val bandwidth: Long,
        val resolution: Int
    )

    suspend fun resolveDurationMs(playlistUrl: String, referer: String? = null): Long? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    parsePlaylist(playlistUrl, referer, depth = 0)?.takeIf { it > 0L }
                } catch (e: Exception) {
                    AppLogger.d("HlsDuration", "resolve failed for $playlistUrl: ${e.message}")
                    null
                }
            }
        }

    private suspend fun parsePlaylist(url: String, referer: String?, depth: Int): Long? {
        if (depth > MAX_DEPTH) return null
        val content = fetch(url, referer) ?: return null
        if (content.contains("#EXT-X-STREAM-INF")) {
            val variant = pickBestVariant(content) ?: return null
            return parsePlaylist(resolveRelativeUrl(url, variant.url), referer, depth + 1)
        }
        return sumExtinf(content)
    }

    internal fun pickBestVariant(content: String): Variant? {
        var best: Variant? = null
        for (match in STREAM_INF.findAll(content)) {
            val attrs = match.groupValues[1]
            val url = match.groupValues[2].trim()
            if (url.isEmpty() || url.startsWith("#")) continue
            val bandwidth = Regex("""BANDWIDTH=(\d+)""").find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val resolution = Regex("""RESOLUTION=(\d+)x\d+""").find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val candidate = Variant(url, bandwidth, resolution)
            if (best == null ||
                candidate.bandwidth > best.bandwidth ||
                (candidate.bandwidth == best.bandwidth && candidate.resolution > best.resolution)
            ) {
                best = candidate
            }
        }
        return best
    }

    internal fun sumExtinf(content: String): Long {
        var totalSeconds = 0.0
        for (match in EXTINF.findAll(content)) {
            totalSeconds += match.groupValues[1].toDoubleOrNull() ?: 0.0
        }
        return Math.round(totalSeconds * 1000.0)
    }

    internal fun resolveRelativeUrl(baseUrl: String, target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        val base = baseUrl.substringBeforeLast("/")
        return when {
            target.startsWith("//") -> baseUrl.substringBefore(":") + ":" + target
            target.startsWith("/") -> baseUrl.substringBefore("//") + "//" + baseUrl.substringAfter("//").substringBefore("/") + target
            else -> "$base/$target"
        }
    }

    private suspend fun fetch(url: String, referer: String?): String? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", Constants.USER_AGENT)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
            referer?.takeIf { it.isNotBlank() }?.let { builder.header("Referer", it) }

            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    response.body?.close()
                    AppLogger.w("HlsDuration", "GET $url -> ${response.code}")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            AppLogger.d("HlsDuration", "fetch failed for $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8_000L
        private const val MAX_DEPTH = 2
        private val EXTINF = Regex("""(?m)^#EXTINF:\s*([0-9]+(?:\.[0-9]+)?)""")
        private val STREAM_INF = Regex("""(?m)^#EXT-X-STREAM-INF:([^\n]*)\n\s*([^\n]+)""")
    }
}
