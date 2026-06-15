package ua.ukrtv.app.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.data.providers.MediaSource as ProviderMediaSource
import ua.ukrtv.app.data.providers.StreamManager
import ua.ukrtv.app.data.network.HtmlHttpClient
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class StreamResolver @Inject constructor(
    private val streamManager: StreamManager,
    private val htmlHttpClient: HtmlHttpClient,
    private val unifiedStreamProvider: UnifiedStreamProvider
) {

    private val streamResolutionCache = ua.ukrtv.app.data.cache.TtlLruCache<String, StreamResolutionResult?>(
        maxSize = 100,
        ttlMs = ua.ukrtv.app.Constants.STREAM_RESOLUTION_CACHE_TTL_MS
    )

    private val hlsExtractor = HlsExtractor()

    private val youtubeDomains = listOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com",
        "ytimg.com", "googlevideo.com", "yt.be"
    )

    private val forbiddenPatterns = listOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com",
        "trailer", "preview", "preview.", "embed",
        "трейлер", "прев'ю", "превью",
        "watch?v=", "shorts/"
    )

    /**
     * Main entry point for resolving any URL (page or episode).
     */
    suspend fun resolve(url: String, referer: String = ""): StreamResolutionResult? = withContext(Dispatchers.IO) {
        Log.d("StreamResolver", "resolve: url=$url, referer=$referer")

        val cacheKey = "resolve|$url|$referer"
        streamResolutionCache.get(cacheKey)?.let { cached ->
            return@withContext cached
        }

        if (isForbiddenUrl(url)) {
            Log.w("StreamResolver", "Forbidden URL, not passing to player: $url")
            return@withContext null
        }

        // 1. If it's already a direct HLS/MPD link and NOT a VOD ID link
        if (isDirectStreamUrl(url) && !isVodIdUrl(url)) {
            Log.i("StreamResolver", "Direct stream URL: $url")
            val resolvedReferer = referer.ifEmpty { inferReferer(url) }
            return@withContext StreamResolutionResult(
                streamUrl = url,
                streamType = getStreamType(url),
                referer = resolvedReferer,
                sourcePageUrl = url
            )
        }

        // 2. If it's a VOD link that needs resolution (url like /vod/<id>)
        if (isVodIdUrl(url)) {
            val effectiveReferer = referer.ifEmpty { inferReferer(url) }
            val resolvedLinks = resolveVodId(url, effectiveReferer)
            if (resolvedLinks.isNotEmpty()) {
                val primary = resolvedLinks.first()
                return@withContext StreamResolutionResult(
                    streamUrl = primary.url,
                    streamType = primary.type,
                    referer = effectiveReferer,
                    fallbackStreams = resolvedLinks.drop(1).map { it.url },
                    sourcePageUrl = url
                )
            }
        }

        // 3. Try using StreamManager (providers)
        val source = streamManager.getStream(url)

        if (source == null) {
            // Агресивна перевірка: якщо це сторінка серіалу провайдера, 
            // не дозволяємо фолбек на iframe GET HTML занадто рано, якщо POST мав спрацювати.
            // Але якщо зовсім нічого не знайшли, пробуємо iframe як останній шанс.
            val isProviderPage = url.contains("uakino") || url.contains("eneyida") || url.contains("uaserials")
            
            if (url.contains("ashdi") || url.contains("hdvb") || url.contains("vidcache") || !isProviderPage) {
                val resolvedLinks = resolveIframe(url, referer)
                if (resolvedLinks.isNotEmpty()) {
                    val primary = resolvedLinks.first()
                    return@withContext StreamResolutionResult(
                        streamUrl = primary.url,
                        streamType = primary.type,
                        referer = referer.ifEmpty { inferReferer(url) },
                        fallbackStreams = resolvedLinks.drop(1).map { it.url },
                        sourcePageUrl = url
                    )
                }
            }
            Log.w("StreamResolver", "No stream resolved for: $url")
            return@withContext null
        }

        var streamUrl = source.primaryUrl ?: return@withContext null
        var fallbackUrls = extractFallbacks(source)

        if (isVodIdUrl(streamUrl)) {
            // will be resolved below
        } else {
            // additionally guard fallback list
            fallbackUrls = fallbackUrls.filterNot { isVodIdUrl(it) }
        }


        // Hard rule: never pass `/vod/<id>` to the player.
        if (isVodIdUrl(streamUrl)) {
            val resolved = resolveVodId(streamUrl, source.referer)
            if (resolved.isNotEmpty()) {
                streamUrl = resolved.first().url
                fallbackUrls = (resolved.drop(1).map { it.url } + fallbackUrls).distinct()
            }
        }

        if (isForbiddenUrl(streamUrl)) {
            Log.w("StreamResolver", "Forbidden stream URL resolved, skipping: $streamUrl")
            return@withContext null
        }

        val result = StreamResolutionResult(
            streamUrl = streamUrl,
            streamType = getStreamType(streamUrl),
            referer = source.referer.ifEmpty { referer.ifEmpty { inferReferer(url) } },
            fallbackStreams = fallbackUrls,
            providerName = source.providerName,
            sourcePageUrl = url,
            source = source
        )

        streamResolutionCache.put(cacheKey, result)
        return@withContext result
    }

    /**
     * Specific method for page URL resolution.
     */
    suspend fun resolvePage(pageUrl: String): StreamResolutionResult? = resolve(pageUrl)

    /**
     * Specific method for episode URL resolution.
     */
    suspend fun resolveEpisode(episodeUrl: String, referer: String = ""): StreamResolutionResult? = 
        resolve(episodeUrl, referer)

    /**
     * Resolves an iframe to a list of ready stream links.
     */
    suspend fun resolveIframe(iframeUrl: String, referer: String): List<HlsExtractor.ExtractResult> = withContext(Dispatchers.IO) {
        val stream = if (iframeUrl.contains("/vod/")) {
            val videoID = iframeUrl.substringAfterLast("/")
            unifiedStreamProvider.getStreamUrl(videoID)
        } else null
        
        if (stream != null) {
            listOf(HlsExtractor.ExtractResult(stream, getStreamType(stream)))
        } else {
            // Fallback to legacy HLS extraction if Unified failed or not applicable
            val html = htmlHttpClient.getHtml(iframeUrl, referer) ?: return@withContext emptyList()
            hlsExtractor.extractFromHtml(html)
        }
    }

    /**
     * Resolves a VOD ID URL to a list of ready stream links.
     */
    suspend fun resolveVodId(vodIdUrl: String, referer: String): List<HlsExtractor.ExtractResult> = withContext(Dispatchers.IO) {
        val videoID = vodIdUrl.substringAfterLast("/")
        val stream = unifiedStreamProvider.getStreamUrl(videoID)
        if (stream != null) {
            listOf(HlsExtractor.ExtractResult(stream, getStreamType(stream)))
        } else {
            emptyList()
        }
    }

    /**
     * Extracts HLS/MPD links from HTML content.
     */
    fun extractHls(html: String): List<HlsExtractor.ExtractResult> {
        return hlsExtractor.extractFromHtml(html)
    }

    /**
     * Selects and deduplicates fallback streams.
     *
     * Phase-1 rule: fallback streams must be ready manifests only (.m3u8/.mpd)
     * to avoid ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED.
     */
    fun fallbackStreams(source: ProviderMediaSource): List<String> {
        return source.allUrls
            .asSequence()
            .filter { it.isNotBlank() && !isForbiddenUrl(it) }
            .distinct()
            .take(8)
            .filter { it.contains(".m3u8", ignoreCase = true) || it.contains(".mpd", ignoreCase = true) }
            .toList()
    }


    private fun isVodIdUrl(url: String): Boolean {
        val lower = url.lowercase()
        // It's a VOD ID if it contains /vod/ but is NOT a direct manifest link
        return lower.contains("/vod/") && !isDirectStreamUrl(url)
    }

    private fun isDirectStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd")
    }

    private fun isForbiddenUrl(url: String): Boolean {
        val lower = url.lowercase()
        return forbiddenPatterns.any { lower.contains(it) } ||
            youtubeDomains.any { lower.contains(it) }
    }

    private fun getStreamType(url: String): StreamType {
        return when {
            url.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
            url.contains(".mpd", ignoreCase = true) -> StreamType.MPD
            url.contains(".mp4", ignoreCase = true) -> StreamType.MP4
            else -> StreamType.UNKNOWN
        }
    }

    private fun inferReferer(url: String): String {
        return when {
            url.contains("ashdi") || url.contains("hdvb") || url.contains("uakino") -> "https://uakino.best/"
            url.contains("eneyida") -> "https://eneyida.tv/"
            url.contains("uaserials") -> "https://uaserials.my/"
            else -> ""
        }
    }

    private fun extractFallbacks(source: ProviderMediaSource): List<String> {
        return fallbackStreams(source)
    }

    fun isResolvable(url: String): Boolean {
        return (isDirectStreamUrl(url) || isVodIdUrl(url)) && !isForbiddenUrl(url)
    }

    fun needsResolution(url: String): Boolean {
        if (isForbiddenUrl(url)) return false
        return !isDirectStreamUrl(url) || url.contains("/vod/")
    }
}
