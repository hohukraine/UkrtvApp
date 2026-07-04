package ua.ukrtv.app.data.streaming.strategies

import ua.ukrtv.app.data.streaming.*
import ua.ukrtv.app.domain.model.StreamResolutionResult
import javax.inject.Inject

class DirectLinkStrategy @Inject constructor() : ResolutionStrategy {
    override val name: String = "DirectLink"

    override suspend fun canHandle(url: String, context: ResolutionContext): Boolean {
        return isDirectStreamUrl(url) && !isVodIdUrl(url)
    }

    override suspend fun resolve(url: String, context: ResolutionContext): StreamResolutionResult? {
        val referer = context.referer.ifEmpty { inferReferer(url) }
        return StreamResolutionResult(
            streamUrl = url,
            streamType = getStreamType(url),
            referer = referer,
            sourcePageUrl = url
        )
    }
}
