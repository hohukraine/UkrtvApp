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
    private val durationCache = ua.ukrtv.app.data.TtlLruCache<String, Long>(
        maxSize = 64,
        ttlMs = 30 * 60 * 1000L // 30 хв
    )

    data class Variant(
        val url: String,
        val bandwidth: Long,
        val resolution: Int
    )

    suspend fun resolveDurationMs(playlistUrl: String, referer: String? = null): Long? =
        withContext(Dispatchers.IO) {
            durationCache.get(playlistUrl)?.let { return@withContext it }
            
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    parsePlaylist(playlistUrl, referer, depth = 0)?.takeIf { it > 0L }?.also {
                        durationCache.put(playlistUrl, it)
                    }
                } catch (e: Exception) {
                    AppLogger.d("HlsDuration", "resolve failed for $playlistUrl: ${e.message}")
                    null
                }
            }
        }

    private suspend fun parsePlaylist(url: String, referer: String?, depth: Int): Long? {
        if (depth > MAX_DEPTH) return null
        val content = fetch(url, referer) ?: return null
        if (isMpd(content)) return parseMpdDuration(content)
        if (content.contains("#EXT-X-STREAM-INF")) {
            val variant = pickBestVariant(content) ?: return null
            return parsePlaylist(resolveRelativeUrl(url, variant.url), referer, depth + 1)
        }
        return sumExtinf(content)
    }

    private fun isMpd(content: String): Boolean {
        val trimmed = content.trimStart()
        return trimmed.startsWith("<?xml") || content.contains("<MPD") || content.contains("mediaPresentationDuration")
    }

    /**
     * DASH/MPD duration. Prefers the explicit `mediaPresentationDuration` attribute; otherwise
     * falls back to summing the segment timelines (`<S d=.. r=..>`) of the longest variant.
     */
    internal fun parseMpdDuration(content: String): Long? {
        mediaPresentationDurationRegex.find(content)?.let { match ->
            parseIso8601Duration(match.groupValues[1])?.let { return it }
        }
        return sumMpdSegments(content)
    }

    internal fun parseIso8601Duration(value: String): Long? {
        val match = ISO8601_DURATION.find(value) ?: return null
        val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0
        if (hours == 0.0 && minutes == 0.0 && seconds == 0.0) return null
        return Math.round((hours * 3600 + minutes * 60 + seconds) * 1000.0)
    }

    internal fun sumMpdSegments(content: String): Long? {
        var best: Long? = null
        for (block in SEGMENT_BLOCK.findAll(content)) {
            val timescale = Regex("""timescale="(\d+)"""")
                .find(block.groupValues[2])?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            var total = 0.0
            for (s in SEGMENT.findAll(block.groupValues[3])) {
                val d = Regex("""\bd="([0-9.]+)"""").find(s.value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val r = Regex("""\br="(\d+)"""").find(s.value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                total += d * (r + 1)
            }
            if (total <= 0.0) continue
            val ms = Math.round(total / timescale * 1000.0)
            if (best == null || ms > best) best = ms
        }
        return best
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
        private val mediaPresentationDurationRegex = Regex("""mediaPresentationDuration="([^"]+)"""")
        private val ISO8601_DURATION = Regex("""^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$""")
        private val SEGMENT_BLOCK = Regex("""<(SegmentTemplate|SegmentList)([^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        private val SEGMENT = Regex("""<S\b[^>]*?>""")
    }
}
