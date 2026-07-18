package ua.ukrtv.app.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.data.repository.WatchlistRepository
import ua.ukrtv.app.data.repository.Top200Repository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.HomePreferences
import ua.ukrtv.app.util.HomeLayout
import ua.ukrtv.app.util.PosterColorCache
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: ContentRepository,
    private val providerManager: ProviderManager,
    private val top200Repository: Top200Repository,
    private val watchlistRepository: WatchlistRepository,
    private val homePreferences: HomePreferences
) : ViewModel() {

    val homeLayout: StateFlow<HomeLayout> = homePreferences.layout

    val isOnline: StateFlow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun currentStatus(): Boolean {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        trySend(currentStatus())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                trySend(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        val request = NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _focusedMovie = MutableStateFlow<Movie?>(null)

    private val _focusColor = MutableStateFlow(Color(0xFF1A1A1A))

    private val _navigateToDetail = MutableSharedFlow<Movie>()
    val navigateToDetail: SharedFlow<Movie> = _navigateToDetail.asSharedFlow()

    private val _navigateToSearch = MutableSharedFlow<String>()
    val navigateToSearch: SharedFlow<String> = _navigateToSearch.asSharedFlow()

    private val _top200 = MutableStateFlow(top200Repository.getRandom5())

    private val _gridError = MutableStateFlow<String?>(null)
    val gridError: StateFlow<String?> = _gridError

    private val _retryTrigger = MutableStateFlow(0L)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedColor: Long = 0xFF6E85B7

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
    val grid: StateFlow<List<Movie>> = combine(
        providerManager.activeProvider,
        _retryTrigger
    ) { provider, _ -> provider }
        .flatMapLatest { provider ->
            _gridError.value = null
            mediaRepository.getHomeGrid(provider)
                .onStart { emit(emptyList()) }
                .catch { e ->
                    _gridError.value = e.message ?: "Помилка завантаження"
                    emit(emptyList())
                }
        }
        .onEach { if (it.isNotEmpty()) _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var stableTrending: List<Movie> = emptyList()
    private var stableTrendingIds: Set<String> = emptySet()

    private fun stabilizeTrending(list: List<Movie>): List<Movie> {
        val ids = list.map { it.pageUrl }.toSet()
        if (ids == stableTrendingIds) {
            if (ua.ukrtv.app.BuildConfig.DEBUG) {
                ua.ukrtv.app.util.AppLogger.d("HomeVM", "stabilizeTrending: SAME ids, returning stable ref @${System.identityHashCode(stableTrending)}")
            }
            return stableTrending
        }
        stableTrendingIds = ids
        val result = if (list.size > 15) list.shuffled().take(15) else list
        stableTrending = result
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            ua.ukrtv.app.util.AppLogger.d("HomeVM", "stabilizeTrending: NEW ids, created ${result.size} items @${System.identityHashCode(result)}")
        }
        return result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val homeTrending: StateFlow<List<Movie>> = grid
        .onEach { if (ua.ukrtv.app.BuildConfig.DEBUG) ua.ukrtv.app.util.AppLogger.d("HomeVM", "grid emitted ${it.size} items @${System.identityHashCode(it)}") }
        .map { list -> stabilizeTrending(list) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<Movie>> = mediaRepository.getContinueWatching()
        .combine(_dismissedIds) { list, dismissed ->
            list.filter { it.id !in dismissed }
        }
        .map { it.take(20) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlist: StateFlow<List<Movie>> = watchlistRepository.getAllWatchlistAsMovies()
        .onStart { emit(emptyList()) }
        .catch { emit(emptyList()) }
        .map { it.take(20) }
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
    val trendingLabel: StateFlow<String> = currentProviderId
        .map { provider ->
            val period = when (provider) {
                "Eneyida" -> "за весь час"
                "Uakino" -> "2026"
                else -> ""
            }
            if (period.isNotEmpty()) "Тренди · $provider · $period" else "Тренди"
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Тренди")

    @OptIn(ExperimentalCoroutinesApi::class)
    val top200: StateFlow<List<Top200Movie>> = _top200
        .stateIn(viewModelScope, SharingStarted.Eagerly, top200Repository.getRandom5())

    @OptIn(ExperimentalCoroutinesApi::class)
    val focusedMovie: StateFlow<Movie?> = _focusedMovie
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val focusColor: StateFlow<Color> = _focusColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, Color(0xFF1A1A1A))

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryMovies: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            flow {
                try {
                    val items = provider.getMoviesByCategory(ContentCategory.MOVIES, 1)
                    emit(items)
                } catch (_: Exception) {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val categorySeries: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            flow {
                try {
                    val items = provider.getMoviesByCategory(ContentCategory.SERIES, 1)
                    emit(items)
                } catch (_: Exception) {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryAnime: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            flow {
                try {
                    val items = provider.getMoviesByCategory(ContentCategory.ANIME, 1)
                    emit(items)
                } catch (_: Exception) {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryCartoons: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            flow {
                try {
                    val items = provider.getMoviesByCategory(ContentCategory.CARTOONS, 1)
                    emit(items)
                } catch (_: Exception) {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryCartoonSeries: StateFlow<List<Movie>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            flow {
                try {
                    val items = provider.getMoviesByCategory(ContentCategory.CARTOON_SERIES, 1)
                    emit(items)
                } catch (_: Exception) {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun retryGrid() {
        _gridError.value = null
        _retryTrigger.value++
    }

    fun onTop200BannerClick(movie: Top200Movie) {
        viewModelScope.launch {
            val query = movie.searchQueries.firstOrNull() ?: movie.title
            _navigateToSearch.emit(query)
        }
    }
}
