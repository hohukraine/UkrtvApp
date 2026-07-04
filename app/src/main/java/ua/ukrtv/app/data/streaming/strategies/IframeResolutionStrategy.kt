package ua.ukrtv.app.data.streaming.strategies

import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.streaming.*
import ua.ukrtv.app.domain.model.StreamResolutionResult
import javax.inject.Inject

class IframeResolutionStrategy @Inject constructor(
    private val htmlHttpClient: ua.ukrtv.app.data.network.HtmlHttpClient,
    private val hlsExtractor: ua.ukrtv.app.data.streaming.HlsExtractor,
    private val logger: ua.ukrtv.app.util.ResolutionLogger
) : ResolutionStrategy {
    override val name: String = "Iframe"

    override suspend fun canHandle(url: String, context: ResolutionContext): Boolean {
        return url.contains("ashdi") || url.contains("hdvb") || url.contains("vidmoly") || url.contains("mcloud") ||
                (!url.contains("uakino") && !url.contains("eneyida") && !isDirectStreamUrl(url))
    }

    override suspend fun resolve(url: String, context: ResolutionContext): StreamResolutionResult? {
        val referer = context.referer.ifEmpty { inferReferer(url) }
        
        logger.log(url, name, "Fetching HTML for iframe extraction")
        val html = try {
            htmlHttpClient.getHtml(url, referer)
        } catch (e: Exception) {
            logger.log(url, name, "HTML fetch failed: ${e.message}", isError = true)
            null
        } ?: return null
        
        val extracted = hlsExtractor.extractFromHtml(html)
        if (extracted.isNotEmpty()) {
            logger.log(url, name, "Extracted ${extracted.size} links from HTML")
            val primary = extracted.first()
            return StreamResolutionResult(
                streamUrl = primary.url,
                streamType = primary.type,
                referer = referer,
                fallbackStreams = extracted.drop(1).map { it.url },
                sourcePageUrl = url
            )
        }
        
        logger.log(url, name, "No links found in HTML", isError = true)
        return null
    }
}
