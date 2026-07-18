package ua.ukrtv.app.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.domain.model.Movie
import javax.inject.Inject

@HiltViewModel
class FullCategoryGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerManager: ProviderManager
) : ViewModel() {

    private val categoryKey: String = savedStateHandle["category"] ?: "movies"

    val category: ContentCategory = when (categoryKey) {
        "movies" -> ContentCategory.MOVIES
        "series" -> ContentCategory.SERIES
        "anime" -> ContentCategory.ANIME
        "cartoons" -> ContentCategory.CARTOONS
        "cartoon_series" -> ContentCategory.CARTOON_SERIES
        else -> ContentCategory.MOVIES
    }

    val categoryTitle: String = when (category) {
        ContentCategory.MOVIES -> "Фільми"
        ContentCategory.SERIES -> "Серіали"
        ContentCategory.ANIME -> "Аніме"
        ContentCategory.CARTOONS -> "Мультфільми"
        ContentCategory.CARTOON_SERIES -> "Мультсеріали"
        else -> "Контент"
    }

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

    init {
        loadCategory()
    }

    private fun loadCategory() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val provider = providerManager.activeProvider.value
                    val pages = (1..4).map { page ->
                        async { provider.getMoviesByCategory(category, page) }
                    }.awaitAll().flatten()
                    pages.distinctBy { it.pageUrl }.take(100)
                }
                _items.value = result
            } catch (e: Exception) {
                _error.value = e.message ?: "Помилка завантаження"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadCategory()
}
