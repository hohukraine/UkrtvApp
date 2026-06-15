package ua.ukrtv.app.data.providers

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class StreamManager @Inject constructor(
    private val providerManager: ProviderManager
) {
    private val activeProvider get() = providerManager.activeProvider.value

    open suspend fun getStream(pageUrl: String): MediaSource? {
        return tryProviders(pageUrl) ?: activeProvider.getMediaSource(pageUrl)
    }

    open suspend fun tryProviders(pageUrl: String): MediaSource? {
        val providers = providerManager.availableProviders
        var lastError: Exception? = null
        for (provider in providers) {
            if (!provider.supportsUrl(pageUrl)) continue
            try {
                val result = provider.getMediaSource(pageUrl)
                if (result != null) {
                    Log.d("StreamManager", "Stream resolved by provider: ${provider.name}")
                    return result
                }
            } catch (e: Exception) {
                lastError = e
                Log.w("StreamManager", "Provider ${provider.name} failed: ${e.message}")
            }
        }
        Log.e("StreamManager", "All providers failed for $pageUrl", lastError)
        return null
    }

    fun clearCache(pageUrl: String) {
        providerManager.availableProviders.forEach { it.clearCache(pageUrl) }
    }
}
