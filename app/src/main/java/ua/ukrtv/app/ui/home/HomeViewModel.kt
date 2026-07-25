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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    data class HomeMainState(
        val isLoading: Boolean = true,
        val gridError: String? = null,
        val homeTrending: List<Movie> = emptyList(),
        val continueWatching: List<Movie> = emptyList(),
        val watchlist: List<Movie> = emptyList(),
    )

    data class HomeCategoriesState(
        val categoryMovies: List<Movie> = emptyList(),
        val categorySeries: List<Movie> = emptyList(),
        val categoryAnime: List<Movie> = emptyList(),
        val categoryCartoons: List<Movie> = emptyList(),
        val categoryCartoonSeries: List<Movie> = emptyList(),
    )

    data class HomeHeroState(
        val bannerMovies: List<Movie> = emptyList(),
        val top200Banners: List<Top200Movie> = emptyList(),
    )

    data class HomeConfigState(
        val isOnline: Boolean = true,
        val brandColor: Long = 0xFF6E85B7,
        val currentProviderId: String = "",
        val trendingLabel: String = "Тренди",
        val homeLayout: HomeLayout = HomeLayout(),
    )

    private val isOnline: Flow<Boolean> = callbackFlow {
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
    }

    private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _focusedMovie = MutableStateFlow<Movie?>(null)
    private val _focusColor = MutableStateFlow(Color(0xFF1A1A1A))
    private val _gridError = MutableStateFlow<String?>(null)
    private val _retryTrigger = MutableStateFlow(0L)
    private val _isLoading = MutableStateFlow(true)
    private val _bannerTop200 = MutableStateFlow<List<Top200Movie>>(emptyList())

    init {
        _bannerTop200.value = top200Repository.getRandom5()
        viewModelScope.launch {
            providerManager.activeProvider.drop(1).collect {
                _bannerTop200.value = top200Repository.getRandom5()
            }
        }
        viewModelScope.launch {
            providerManager.activeProvider.collect { provider ->
                if (mediaRepository.isHomeCacheStale(provider.name, staleHoursThreshold = 1)) {
                    retryGrid()
                }
            }
        }
    }

    private val _navigateToDetail = MutableSharedFlow<Movie>()
    val navigateToDetail: SharedFlow<Movie> = _navigateToDetail.asSharedFlow()

    private val _navigateToSearch = MutableSharedFlow<String>()
    val navigateToSearch: SharedFlow<String> = _navigateToSearch.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val providerConfig: Flow<Pair<Long, String>> = providerManager.activeProvider
        .map { provider ->
            val colorInt = try { android.graphics.Color.parseColor(provider.brandColor) } catch(_: Exception) { 0xFF6E85B7.toInt() }
            (colorInt.toLong() and 0xFFFFFFFFL) to provider.name
        }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val grid: Flow<List<Movie>> = combine(
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

    private var stableTrending: List<Movie> = emptyList()
    private var stableTrendingIds: Set<String> = emptySet()

    private fun stabilizeTrending(list: List<Movie>): List<Movie> {
        val ids = list.map { it.pageUrl }.toSet()
        if (ids == stableTrendingIds) return stableTrending
        stableTrendingIds = ids
        val result = if (list.size > 15) list.shuffled().take(15) else list
        stableTrending = result
        return result
    }

    private val homeTrending: Flow<List<Movie>> = grid
        .map { list -> stabilizeTrending(list) }
        .distinctUntilChanged()
        .onEach { list ->
            viewModelScope.launch {
                list.take(8).forEach { PosterColorCache.getColor(context, it.poster) }
            }
        }

    private val continueWatching: Flow<List<Movie>> = mediaRepository.getContinueWatching()
        .combine(_dismissedIds) { list, dismissed ->
            list.filter { it.id !in dismissed }
        }
        .map { it.take(20) }
        .onEach { list ->
            viewModelScope.launch {
                list.forEach { movie ->
                    PosterColorCache.getColor(context, movie.poster)
                }
            }
        }

    private val watchlist: Flow<List<Movie>> = watchlistRepository.getAllWatchlistAsMovies()
        .onStart { emit(emptyList()) }
        .catch { emit(emptyList()) }
        .map { it.take(20) }

    private val bannerMovies: Flow<List<Movie>> = combine(
        continueWatching,
        grid
    ) { cw, grid ->
        if (cw.isNotEmpty()) cw.take(5) else grid.take(5)
    }

    private val trendingLabel: Flow<String> = providerConfig
        .map { (_, providerName) ->
            val period = when (providerName) {
                "Eneyida" -> "за весь час"
                "Uakino" -> "2026"
                else -> ""
            }
            if (period.isNotEmpty()) "Тренди · $providerName · $period" else "Тренди"
        }

    // 3.2 Combined categories request — parallel fetching
    @OptIn(ExperimentalCoroutinesApi::class)
    private val categories: Flow<Map<ContentCategory, List<Movie>>> = providerManager.activeProvider
        .flatMapLatest { provider ->
            flow {
                val cats = listOf(
                    ContentCategory.MOVIES, ContentCategory.SERIES, ContentCategory.ANIME,
                    ContentCategory.CARTOONS, ContentCategory.CARTOON_SERIES
                )
                val results = kotlinx.coroutines.coroutineScope {
                    cats.map { cat ->
                        async {
                            cat to try { provider.getMoviesByCategory(cat, 1) } catch (_: Exception) { emptyList() }
                        }
                    }.awaitAll().toMap()
                }
                emit(results)
            }
        }
        .onEach { map ->
            viewModelScope.launch {
                map.values.flatten().take(30).forEach { movie ->
                    PosterColorCache.getColor(context, movie.poster)
                }
            }
        }

    val mainContentState: StateFlow<HomeMainState> = combine(
        _isLoading, _gridError, homeTrending, continueWatching, watchlist
    ) { isLoading, gridError, trending, cw, wl ->
        HomeMainState(
            isLoading = isLoading,
            gridError = gridError,
            homeTrending = trending,
            continueWatching = cw,
            watchlist = wl
        )
    }.conflate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeMainState())

    val categoriesState: StateFlow<HomeCategoriesState> = categories.map { cats ->
        HomeCategoriesState(
            categoryMovies = cats[ContentCategory.MOVIES] ?: emptyList(),
            categorySeries = cats[ContentCategory.SERIES] ?: emptyList(),
            categoryAnime = cats[ContentCategory.ANIME] ?: emptyList(),
            categoryCartoons = cats[ContentCategory.CARTOONS] ?: emptyList(),
            categoryCartoonSeries = cats[ContentCategory.CARTOON_SERIES] ?: emptyList()
        )
    }.conflate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeCategoriesState())

    val heroState: StateFlow<HomeHeroState> = combine(
        bannerMovies, _bannerTop200
    ) { banner, top200 ->
        HomeHeroState(
            bannerMovies = banner,
            top200Banners = top200
        )
    }.conflate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeHeroState())

    val configState: StateFlow<HomeConfigState> = combine(
        isOnline, providerConfig, trendingLabel, homePreferences.layout
    ) { isOnline, providerCfg, trendingLabel, homeLayout ->
        HomeConfigState(
            isOnline = isOnline,
            brandColor = providerCfg.first,
            currentProviderId = providerCfg.second,
            trendingLabel = trendingLabel,
            homeLayout = homeLayout
        )
    }.conflate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeConfigState())

    val focusColor: StateFlow<Color> = _focusColor.asStateFlow()
    val focusedMovie: StateFlow<Movie?> = _focusedMovie.asStateFlow()
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
