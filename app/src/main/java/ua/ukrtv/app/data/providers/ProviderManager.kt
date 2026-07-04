package ua.ukrtv.app.data.providers

import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.domain.model.Provider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderManager @Inject constructor(
    private val htmlHttpClient: HtmlHttpClient,
    private val sessionRepository: SessionRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val allProviders: List<MediaProvider> = listOf(
        EneyidaProvider(htmlHttpClient, sessionRepository),
        UakinoProvider(htmlHttpClient, sessionRepository)
    )

    private fun getById(id: String): MediaProvider? =
        allProviders.find { it.name.equals(id, ignoreCase = true) }

    val uakinoProvider: MediaProvider get() = getById("Uakino") ?: allProviders.first()
    val eneyidaProvider: MediaProvider get() = getById("Eneyida") ?: allProviders.first()

    private val _activeProvider = MutableStateFlow<MediaProvider>(eneyidaProvider)
    val activeProvider: StateFlow<MediaProvider> = _activeProvider

    private val _brandColor = MutableStateFlow(Color.parseColor(eneyidaProvider.brandColor))
    val brandColor: StateFlow<Int> = _brandColor

    val availableProviders: List<MediaProvider> get() = allProviders

    fun getProviders(): List<Provider> = availableProviders.map {
        Provider(it.name, it.brandColor)
    }

    fun getProviderForUrl(url: String): MediaProvider? {
        return allProviders.find { it.supportsUrl(url) }
    }

    fun setActiveProvider(providerId: String) {
        val provider = getById(providerId) ?: return
        if (_activeProvider.value.name == provider.name) return
        
        _activeProvider.value = provider
        _brandColor.value = Color.parseColor(provider.brandColor)

        allProviders.forEach { it.clearCache() }

        scope.launch {
            try {
                // DNS Pre-warming
                val host = try { java.net.URI(provider.baseUrl).host } catch(e: Exception) {
                    AppLogger.w("ProviderManager", "Failed to parse host: ${e.message}")
                    null
                }
                if (host != null) {
                    withContext(Dispatchers.IO) {
                        try { java.net.InetAddress.getAllByName(host) } catch(e: Exception) {
                            AppLogger.w("ProviderManager", "DNS pre-warm failed: ${e.message}")
                        }
                    }
                }
                provider.initializeSession()
            } catch (e: Exception) {
                AppLogger.w("ProviderManager", "Provider switch failed: ${e.message}")
            }
        }
    }

    fun clearCaches() {
        allProviders.forEach { it.clearCache() }
    }
}
