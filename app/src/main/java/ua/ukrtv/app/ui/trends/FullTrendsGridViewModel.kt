package ua.ukrtv.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.domain.model.Movie
import javax.inject.Inject

@HiltViewModel
class FullTrendsGridViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val providerManager: ProviderManager
) : ViewModel() {

    private val _items = MutableStateFlow<List<Movie>>(emptyList())
    val items: StateFlow<List<Movie>> = _items

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @OptIn(ExperimentalCoroutinesApi::class)
    val brandColor: StateFlow<Long> = providerManager.activeProvider
        .map { provider ->
            val colorInt = try { android.graphics.Color.parseColor(provider.brandColor) } catch (_: Exception) { 0xFF6E85B7.toInt() }
            (colorInt.toLong() and 0xFFFFFFFFL)
        }
        .distinctUntilChanged()
        .onStart {
            val p = providerManager.activeProvider.value
            val colorInt = try { android.graphics.Color.parseColor(p.brandColor) } catch (_: Exception) { 0xFF6E85B7.toInt() }
            emit((colorInt.toLong() and 0xFFFFFFFFL))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0xFF6E85B7)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentProviderId: StateFlow<String> = providerManager.activeProvider
        .map { it.name }
        .distinctUntilChanged()
        .onStart { emit(providerManager.activeProvider.value.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        loadTrends()
    }

    private fun loadTrends() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    contentRepository.getTrendsForGrid()
                }
                _items.value = result
            } catch (e: Exception) {
                _error.value = e.message ?: "Помилка завантаження"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadTrends()
}
