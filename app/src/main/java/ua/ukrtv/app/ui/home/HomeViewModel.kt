package ua.ukrtv.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.data.repository.WatchlistRepository
import ua.ukrtv.app.data.repository.Top200Repository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PosterColorCache
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

    private val _focusColor = MutableStateFlow(Color(0xFF1A1A1A))

    private val _navigateToDetail = MutableSharedFlow<Movie>()
    val navigateToDetail: SharedFlow<Movie> = _navigateToDetail.asSharedFlow()

    private val _navigateToSearch = MutableSharedFlow<String>()
    val navigateToSearch: SharedFlow<String> = _navigateToSearch.asSharedFlow()

    private val _top200 = MutableStateFlow(top200Repository.getRandom5())

    private var cachedColor: Long = 0xFF6E85B7
    private var cachedProviderId: String = ""

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
    val grid: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            mediaRepository.getHomeGrid(provider)
                .onStart { emit(emptyList()) }
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<Movie>> = mediaRepository.getContinueWatching()
        .combine(_dismissedIds) { list, dismissed ->
            list.filter { it.id !in dismissed }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlist: StateFlow<List<Movie>> = watchlistRepository.getAllWatchlistAsMovies()
        .onStart { emit(emptyList()) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val banner: StateFlow<List<Movie>> = combine(
        continueWatching,
        grid
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
    val top200: StateFlow<List<Top200Movie>> = _top200
        .stateIn(viewModelScope, SharingStarted.Eagerly, top200Repository.getRandom5())

    @OptIn(ExperimentalCoroutinesApi::class)
    val focusedMovie: StateFlow<Movie?> = _focusedMovie
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val focusColor: StateFlow<Color> = _focusColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, Color(0xFF1A1A1A))

    val providers: List<Provider> = providerManager.getProviders()

    private var lastDismissTime = 0L

    fun onMovieFocused(movie: Movie, context: android.content.Context) {
        _focusedMovie.value = movie
        val cached = PosterColorCache.getCached(movie.poster)
        if (cached != null) {
            _focusColor.value = cached
        } else {
            viewModelScope.launch {
                val color = PosterColorCache.getColor(context, movie.poster)
                if (_focusedMovie.value?.id == movie.id) {
                    _focusColor.value = color
                }
            }
        }
    }

    fun provideFocusColor(color: Color) {
        _focusColor.value = color
    }

    fun dismissContinueWatching(movie: Movie) {
        val now = System.currentTimeMillis()
        if (now - lastDismissTime < 500) return
        lastDismissTime = now

        _dismissedIds.value = _dismissedIds.value + movie.id
        viewModelScope.launch {
            mediaRepository.removeFromContinueWatching(movie)
        }
    }

    fun switchProvider(providerId: String) {
        providerManager.setActiveProvider(providerId)
    }

    fun onTop200BannerClick(movie: Top200Movie) {
        viewModelScope.launch {
            val best = mediaRepository.resolveTop200(movie)
            if (best != null) {
                _navigateToDetail.emit(best)
            } else {
                _navigateToSearch.emit(movie.originalTitle.ifEmpty { movie.title })
            }
        }
    }
}
