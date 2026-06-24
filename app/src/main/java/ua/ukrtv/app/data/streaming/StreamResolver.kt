package ua.ukrtv.app.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.data.providers.MediaSource as ProviderMediaSource
import ua.ukrtv.app.data.providers.StreamManager
import ua.ukrtv.app.data.providers.toDomainSeason
import ua.ukrtv.app.data.network.HtmlHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolver @Inject constructor(
    private val streamManager: StreamManager,
    private val htmlHttpClient: HtmlHttpClient,
    private val unifiedStreamProvider: UnifiedStreamProvider
) {

    private val streamResolutionCache = ua.ukrtv.app.data.TtlLruCache<String, StreamResolutionResult?>(
        maxSize = 100,
        ttlMs = 2 * 60 * 1000L
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

    suspend fun resolve(
        url: String,
        referer: String = "",
        season: Int? = null,
        episode: Int? = null,
        isDeep: Boolean = true
    ): StreamResolutionResult? = withContext(Dispatchers.IO) {
        Log.d("StreamResolver", "resolve: url=$url, referer=$referer, s=$season, e=$episode, deep=$isDeep")

        val cacheKey = "resolve|$url|$referer|$season|$episode|$isDeep"
        streamResolutionCache.get(cacheKey)?.let { cached ->
            return@withContext cached
        }

        if (isForbiddenUrl(url)) return@withContext null

        if (isDirectStreamUrl(url) && !isVodIdUrl(url)) {
            val resolvedReferer = referer.ifEmpty { inferReferer(url) }
            return@withContext StreamResolutionResult(
                streamUrl = url,
                streamType = getStreamType(url),
                referer = resolvedReferer,
                sourcePageUrl = url
            )
        }

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

        val source = streamManager.getStream(url, season, episode, isDeep)
        if (source == null) {
            val isProviderPage = url.contains("uakino") || url.contains("eneyida")
            if (url.contains("ashdi") || url.contains("hdvb") || !isProviderPage) {
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
            return@withContext null
        }

        var streamUrl = source.primaryUrl ?: return@withContext null
        var fallbackUrls = fallbackStreams(source)

        if (isVodIdUrl(streamUrl)) {
            val resolved = resolveVodId(streamUrl, source.referer)
            if (resolved.isNotEmpty()) {
                streamUrl = resolved.first().url
                fallbackUrls = (resolved.drop(1).map { it.url } + fallbackUrls).distinct()
            }
        }

        val result = StreamResolutionResult(
            streamUrl = streamUrl,
            streamType = getStreamType(streamUrl),
            referer = source.referer.ifEmpty { referer.ifEmpty { inferReferer(url) } },
            fallbackStreams = fallbackUrls,
            providerName = source.providerName,
            sourcePageUrl = url,
            source = source,
            seasons = if (source is ProviderMediaSource.Series) {
                source.seasons.map { it.toDomainSeason() }
            } else null
        )

        streamResolutionCache.put(cacheKey, result)
        return@withContext result
    }

    suspend fun resolvePage(
        pageUrl: String,
        season: Int? = null,
        episode: Int? = null
    ): StreamResolutionResult? = resolve(pageUrl, season = season, episode = episode)

    suspend fun resolveIframe(iframeUrl: String, referer: String): List<HlsExtractor.ExtractResult> = withContext(Dispatchers.IO) {
        try {
            val stream = if (iframeUrl.contains("/vod/")) {
                val videoID = iframeUrl.substringAfterLast("/")
                unifiedStreamProvider.getStreamUrl(videoID)
            } else null

            if (stream != null) {
                listOf(HlsExtractor.ExtractResult(stream, getStreamType(stream)))
            } else {
                val html = htmlHttpClient.getHtml(iframeUrl, referer) ?: return@withContext emptyList()
                hlsExtractor.extractFromHtml(html)
            }
        } catch (e: Exception) {
            Log.w("StreamResolver", "resolveIframe failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun resolveVodId(vodIdUrl: String, referer: String): List<HlsExtractor.ExtractResult> = withContext(Dispatchers.IO) {
        try {
            val videoID = vodIdUrl.substringAfterLast("/")
            val stream = unifiedStreamProvider.getStreamUrl(videoID)
            if (stream != null) {
                listOf(HlsExtractor.ExtractResult(stream, getStreamType(stream)))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("StreamResolver", "resolveVodId failed: ${e.message}")
            emptyList()
        }
    }

    fun fallbackStreams(source: ProviderMediaSource): List<String> {
        return source.allUrls
            .asSequence()
            .filter { it.isNotBlank() && !isForbiddenUrl(it) }
            .distinct()
            .take(8)
            .filter { it.contains(".m3u8", ignoreCase = true) || it.contains(".mpd", ignoreCase = true) }
            .toList()
    }

    private fun isVodIdUrl(url: String): Boolean = url.lowercase().let { l -> (l.contains("/vod/") || l.startsWith("dleid://")) && !isDirectStreamUrl(url) }
    private fun isDirectStreamUrl(url: String): Boolean = url.lowercase().let { l -> l.contains(".m3u8") || l.contains(".mpd") }
    private fun isForbiddenUrl(url: String): Boolean = url.lowercase().let { l -> forbiddenPatterns.any { l.contains(it) } || youtubeDomains.any { l.contains(it) } }
    private fun getStreamType(url: String): StreamType = when {
        url.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
        url.contains(".mpd", ignoreCase = true) -> StreamType.MPD
        else -> StreamType.MP4
    }
    private fun inferReferer(url: String): String = when {
        url.contains("ashdi") || url.contains("hdvb") || url.contains("uakino") -> "https://uakino.best/"
        url.contains("eneyida") -> "https://eneyida.tv/"
        else -> ""
    }

    fun clearCache(url: String) { streamManager.clearCache(url) }
}
