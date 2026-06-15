package ua.ukrtv.app.data.providers

import android.content.Context
import android.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.Provider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: ProviderRegistry
) {
    private val _activeProvider = MutableStateFlow<ContentProvider>(registry.uakinoProvider)
    val activeProvider: StateFlow<ContentProvider> = _activeProvider

    private val _brandColor = MutableStateFlow(Color.parseColor(registry.uakinoProvider.brandColor))
    val brandColor: StateFlow<Int> = _brandColor

    val availableProviders: List<ContentProvider> get() = registry.providers

    fun getProviders(): List<Provider> = availableProviders.map { 
        Provider(it.javaClass.simpleName, it.name, it.logoUrl, it.brandColor) 
    }

    suspend fun setActiveProvider(providerId: String) {
        val provider = registry.getById(providerId) ?: return
        _activeProvider.value = provider
        updateBrandColor(provider)
    }

    private suspend fun updateBrandColor(provider: ContentProvider) {
        try {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(provider.logoUrl)
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val color = withContext(Dispatchers.Default) {
                        try {
                            val palette = Palette.from(bitmap).generate()
                            palette.getVibrantColor(Color.parseColor(provider.brandColor))
                        } catch (e: Exception) {
                            Color.parseColor(provider.brandColor)
                        }
                    }
                    _brandColor.value = color
                }
            } else {
                _brandColor.value = Color.parseColor(provider.brandColor)
            }
        } catch (e: Exception) {
            _brandColor.value = Color.parseColor(provider.brandColor)
        }
    }
}
