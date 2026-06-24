package ua.ukrtv.app.data.providers

import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.providers.GenericDleProvider
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.data.streaming.UnifiedStreamProvider
import ua.ukrtv.app.domain.model.Provider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderManager @Inject constructor(
    private val client: OkHttpClient,
    private val htmlHttpClient: HtmlHttpClient,
    private val unifiedStreamProvider: UnifiedStreamProvider,
    private val sessionRepository: SessionRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allProviders: List<MediaProvider> by lazy {
        listOf(
            GenericDleProvider(client, htmlHttpClient, unifiedStreamProvider, sessionRepository, UakinoProfile),
            GenericDleProvider(client, htmlHttpClient, unifiedStreamProvider, sessionRepository, EneyidaProfile)
        ).sortedBy { it.name }
    }

    private fun getById(id: String): MediaProvider? =
        allProviders.find { it.name.equals(id, ignoreCase = true) || it.id == id.lowercase() }

    val uakinoProvider: MediaProvider get() = getById("Uakino") ?: allProviders.first()
    val eneyidaProvider: MediaProvider get() = getById("Eneyida") ?: allProviders.first()

    private val _activeProvider = MutableStateFlow<MediaProvider>(uakinoProvider)
    val activeProvider: StateFlow<MediaProvider> = _activeProvider

    private val _brandColor = MutableStateFlow(Color.parseColor(uakinoProvider.brandColor))
    val brandColor: StateFlow<Int> = _brandColor

    val availableProviders: List<MediaProvider> get() = allProviders

    init {
        // Pre-warm the default provider
        scope.launch {
            try { uakinoProvider.initializeSession() } catch (_: Exception) {}
        }
    }

    fun getProviders(): List<Provider> = availableProviders.map {
        Provider(it.name, it.name, it.logoUrl, it.brandColor)
    }

    fun setActiveProvider(providerId: String) {
        val provider = getById(providerId) ?: return
        _activeProvider.value = provider
        _brandColor.value = Color.parseColor(provider.brandColor)

        scope.launch {
            try { provider.initializeSession() } catch (_: Exception) {}
        }
    }
}
