package ua.ukrtv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.data.repository.WatchlistRepository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.data.repository.Top200Repository
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: ContentRepository,
    private val providerManager: ProviderManager,
    private val top200Repository: Top200Repository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _focusedMovie = MutableStateFlow<Movie?>(null)
    private val _top200Banners = MutableStateFlow(top200Repository.getRandom5())

    private var cachedColor: Long = 0xFF6E85B7
    private var cachedProviderId: String = ""

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private val providerConfig: Flow<Pair<Long, String>> = providerManager.activeProvider
        .map { provider ->
            val colorInt = try { android.graphics.Color.parseColor(provider.brandColor) } catch(_: Exception) { 0xFF6E85B7.toInt() }
            (colorInt.toLong() and 0xFFFFFFFFL) to provider.name
        }
        .distinctUntilChanged()
        .onStart {
            val p = providerManager.activeProvider.value
            val colorInt = try { android.graphics.Color.parseColor(p.brandColor) } catch(_: Exception) { 0xFF6E85B7.toInt() }
            emit((colorInt.toLong() and 0xFFFFFFFFL) to p.name)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val gridState: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            mediaRepository.getHomeGrid(provider)
                .onStart { emit(emptyList()) }
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatchingState: StateFlow<List<Movie>> = mediaRepository.getContinueWatching()
        .combine(_dismissedIds) { list, dismissed ->
            list.filter { it.id !in dismissed }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistState: StateFlow<List<Movie>> = watchlistRepository.getAllWatchlistAsMovies()
        .onStart { emit(emptyList()) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bannerState: StateFlow<List<Movie>> = combine(
        continueWatchingState,
        gridState
    ) { cw, grid ->
        if (cw.isNotEmpty()) cw.take(5) else grid.take(5)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val brandColor: StateFlow<Long> = providerConfig
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0xFF6E85B7)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentProviderId: StateFlow<String> = providerConfig
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val top200Banners: StateFlow<List<Top200Movie>> = _top200Banners
        .stateIn(viewModelScope, SharingStarted.Eagerly, top200Repository.getRandom5())

    @OptIn(ExperimentalCoroutinesApi::class)
    val focusedMovie: StateFlow<Movie?> = _focusedMovie
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val providers: List<Provider> = providerManager.getProviders()

    fun onMovieFocused(movie: Movie) {
        _focusedMovie.value = movie
    }

    fun dismissContinueWatching(movie: Movie) {
        _dismissedIds.value = _dismissedIds.value + movie.id
        viewModelScope.launch {
            mediaRepository.removeFromContinueWatching(movie)
        }
    }

    fun switchProvider(providerId: String) {
        providerManager.setActiveProvider(providerId)
    }
}
