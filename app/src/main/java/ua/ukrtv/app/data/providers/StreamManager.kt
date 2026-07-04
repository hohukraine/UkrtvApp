package ua.ukrtv.app.data.providers

import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class StreamManager @Inject constructor(
    private val providerManager: ProviderManager
) {
    private val activeProvider get() = providerManager.activeProvider.value

    open suspend fun getStream(pageUrl: String, season: Int? = null, episode: Int? = null, isDeep: Boolean = true, prefetchedHtml: String? = null): MediaSource? {
        val providerResult = tryProviders(pageUrl, season, episode, isDeep, prefetchedHtml)
        if (providerResult != null) return providerResult
        
        // Only use active provider if it supports the URL or it's a search result from it
        val provider = activeProvider
        if (provider.supportsUrl(pageUrl)) {
            return provider.getMediaSource(pageUrl, season, episode, isDeep, prefetchedHtml)
        }
        
        return null
    }

    open suspend fun tryProviders(pageUrl: String, season: Int? = null, episode: Int? = null, isDeep: Boolean = true, prefetchedHtml: String? = null): MediaSource? {
        val providers = providerManager.availableProviders
        var lastError: Exception? = null
        for (provider in providers) {
            if (!provider.supportsUrl(pageUrl)) continue
            try {
                val result = provider.getMediaSource(pageUrl, season, episode, isDeep, prefetchedHtml)
                if (result != null) {
                    AppLogger.d("StreamManager", "Stream resolved by provider: ${provider.name} (deep=$isDeep)")
                    return result
                }
            } catch (e: Exception) {
                lastError = e
                AppLogger.w("StreamManager", "Provider ${provider.name} failed: ${e.message}")
            }
        }
        AppLogger.w("StreamManager", "All providers failed for $pageUrl", lastError)
        return null
    }

    fun clearCache(pageUrl: String) {
        providerManager.availableProviders.forEach { it.clearCache(pageUrl) }
    }
}
