package ua.ukrtv.app.data.streaming.strategies

import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.providers.StreamManager
import ua.ukrtv.app.data.providers.toDomainSeason
import ua.ukrtv.app.data.streaming.*
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject

class ProviderResolutionStrategy @Inject constructor(
    private val streamManager: StreamManager
) : ResolutionStrategy {
    override val name: String = "Provider"

    override suspend fun canHandle(url: String, context: ResolutionContext): Boolean {
        return url.contains("uakino") || url.contains("eneyida")
    }

    override suspend fun resolve(url: String, context: ResolutionContext): StreamResolutionResult? {
        val source = try {
            streamManager.getStream(url, context.season, context.episode, context.isDeep, context.prefetchedHtml)
        } catch (e: Exception) {
            null
        } ?: return null

        var streamUrl = source.primaryUrl ?: return null
        var fallbackUrls = fallbackStreams(source)

        if (source is MediaSource.Series && context.season != null) {
            val s = source.seasons.find { it.number == context.season }
            if (s == null) {
                AppLogger.w("ProviderStrategy", "Season ${context.season} not found in ${source.seasons.size} seasons for $url")
            }
            val ep = if (context.episode != null) s?.episodes?.find { it.number == context.episode } else s?.episodes?.firstOrNull()
            if (ep == null) {
                AppLogger.w("ProviderStrategy", "Episode ${context.episode} not found in season ${context.season} for $url")
            }
            if (ep != null) {
                streamUrl = if (context.voiceover != null) {
                    s?.voiceovers?.find { it.name == context.voiceover }
                        ?.episodes?.find { it.number == (context.episode ?: ep.number) }?.url ?: ep.url
                } else {
                    ep.url
                }
                fallbackUrls = (s?.episodes?.map { it.url }?.filter { it != streamUrl } ?: emptyList()) + fallbackUrls
            }
        }

        return StreamResolutionResult(
            streamUrl = streamUrl,
            streamType = getStreamType(streamUrl),
            referer = source.referer.ifEmpty { context.referer.ifEmpty { inferReferer(url) } },
            fallbackStreams = fallbackUrls.distinct(),
            providerName = source.providerName,
            sourcePageUrl = url,
            seasons = if (source is MediaSource.Series) {
                source.seasons.map { it.toDomainSeason() }
            } else null
        )
    }

    private fun fallbackStreams(source: MediaSource): List<String> {
        return source.allUrls
            .asSequence()
            .filter { it.isNotBlank() && !isForbiddenUrl(it) }
            .distinct()
            .take(8)
            .filter {
                val clean = it.substringBefore("?").substringBefore("#")
                clean.endsWith(".m3u8", ignoreCase = true) || clean.endsWith(".mpd", ignoreCase = true)
            }
            .toList()
    }
}
