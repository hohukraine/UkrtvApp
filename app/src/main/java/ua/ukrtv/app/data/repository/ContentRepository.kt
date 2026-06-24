package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamResolutionResult

import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.providers.ContentUtils
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ContentRepository @Inject constructor(
    private val providerManager: ProviderManager,
    private val watchProgressRepository: WatchProgressRepository,
    private val streamResolver: StreamResolver,
    private val homeCacheRepository: HomeCacheRepository,
    private val searchCacheDao: ua.ukrtv.app.data.local.dao.SearchCacheDao,
    private val htmlCacheDao: ua.ukrtv.app.data.local.dao.HtmlCacheDao,
    private val gson: com.google.gson.Gson
) {
    private val activeProvider get() = providerManager.activeProvider.value

    private val popularCache = TtlLruCache<String, List<Movie>>(maxSize = 20, ttlMs = 15 * 60 * 1000L)
    private val searchCache = TtlLruCache<String, List<Movie>>(maxSize = 50, ttlMs = Constants.SEARCH_CACHE_TTL_MS)
    private val metadataCache = TtlLruCache<String, MovieDetail>(maxSize = 200, ttlMs = Constants.METADATA_CACHE_TTL_MS)
    private val navigationCache = TtlLruCache<String, MovieDetail>(maxSize = 50, ttlMs = 30 * 60 * 1000L)
    
    private val searchSemaphore = Semaphore(3)

    init {
        cleanupOldCaches()
    }

    private fun cleanupOldCaches() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                htmlCacheDao.deleteOldCache(now - (3 * 24 * 60 * 60 * 1000L))
                searchCacheDao.deleteOld(now - (7 * 24 * 60 * 60 * 1000L))
            } catch (e: Exception) {
                AppLogger.e("ContentRepository", "Cache cleanup failed", e)
            }
        }
    }

    fun getHomeSections(): Flow<Result<List<HomeSection>>> = flow {
        val providerName = activeProvider.name
        val cached = homeCacheRepository.getHomeCache(providerName)
        if (cached != null) {
            emit(Result.success(cached))
        }

        try {
            val sections = mutableListOf<HomeSection>()
            val TRENDS_LIMIT = 35
            val MOVIES_LIMIT = 20
            val SERIES_LIMIT = 15

            val trends = try {
                activeProvider.getMoviesByCategory(ContentCategory.TRENDS, page = 1).take(TRENDS_LIMIT)
            } catch (e: Exception) {
                AppLogger.e("ContentRepository", "Trends fetch failed", e)
                emptyList()
            }
            if (trends.isNotEmpty()) {
                sections.add(HomeSection("Тренди", trends))
            } else {
                coroutineScope {
                    val moviesDef = async {
                        try {
                            activeProvider.getMoviesByCategory(ContentCategory.MOVIES, page = 1).take(MOVIES_LIMIT)
                        } catch (e: Exception) {
                            AppLogger.e("ContentRepository", "Provider movies failed", e)
                            emptyList()
                        }
                    }
                    val seriesDef = async {
                        try {
                            activeProvider.getMoviesByCategory(ContentCategory.SERIES, page = 1).take(SERIES_LIMIT)
                        } catch (e: Exception) {
                            AppLogger.e("ContentRepository", "Provider series failed", e)
                            emptyList()
                        }
                    }

                    val movies = moviesDef.await()
                    if (movies.isNotEmpty()) sections.add(HomeSection("Фільми онлайн", movies))

                    val series = seriesDef.await()
                    if (series.isNotEmpty()) sections.add(HomeSection("Серіали онлайн", series))
                }
            }

            if (sections.isNotEmpty()) {
                if (sections != cached) {
                    emit(Result.success(sections))
                    homeCacheRepository.saveHomeCache(providerName, sections)
                }
            } else if (cached == null) {
                emit(Result.success(emptyList()))
            }
        } catch (e: Exception) {
            AppLogger.e("ContentRepository", "Failed to fetch home sections", e)
            if (cached == null) emit(Result.success(emptyList()))
        }
    }.flowOn(Dispatchers.IO)


    fun getContinueWatching(): Flow<List<Movie>> = flow {
        val allProgress = watchProgressRepository.getAllProgress()
        val movies = allProgress
            .filter { it.progressPercentage in 1..94 }
            .map { progress ->
                val pUrl = progress.pageUrl
                val poster = progress.poster.orEmpty()
                val rawTitle = progress.title ?: ""
                val cleanTitle = ContentUtils.cleanTitle(rawTitle)

                // Cross-provider: if pageUrl doesn't match current provider, search by title
                val resolvedUrl = if (pUrl.isNotEmpty() && !activeProvider.supportsUrl(pUrl)) {
                    try {
                        val results = activeProvider.search(cleanTitle.ifEmpty { rawTitle }, limit = 5)
                        val match = results.firstOrNull { result ->
                            ContentUtils.isTitleMatch(result.title, rawTitle)
                        }
                        if (match != null) {
                            AppLogger.d("ContentRepository", "Cross-provider match for '$cleanTitle': ${match.url}")
                            // Don't replace poster — keep original for instant cache hit
                            watchProgressRepository.saveProgress(
                                contentId = progress.contentId,
                                episodeId = null,
                                positionMs = progress.positionMs,
                                durationMs = progress.durationMs,
                                title = rawTitle,
                                poster = poster,
                                pageUrl = match.url
                            )
                            match.url
                        } else pUrl
                    } catch (e: Exception) {
                        AppLogger.w("ContentRepository", "Cross-provider search failed for '$cleanTitle': ${e.message}")
                        pUrl
                    }
                } else pUrl

                Movie(
                    id = progress.contentId,
                    title = cleanTitle,
                    poster = poster,
                    year = null,
                    pageUrl = resolvedUrl,
                    type = ContentUtils.inferContentType(resolvedUrl, rawTitle),
                    watchProgress = progress.progressPercentage
                )
            }
        emit(movies)
    }.flowOn(Dispatchers.IO)

    suspend fun removeFromContinueWatching(movie: Movie) {
        watchProgressRepository.deleteProgress(movie.id)
    }

    fun getPopularByCategory(category: ContentCategory): Flow<List<Movie>> = flow {
        val cacheKey = "popular|${activeProvider.name}|$category"
        popularCache.get(cacheKey)?.let {
            emit(it)
            return@flow
        }
        
        try {
            val movies = activeProvider.getMoviesByCategory(category)
            if (movies.isNotEmpty()) {
                popularCache.put(cacheKey, movies)
                emit(movies) 
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<Result<List<Movie>>> = flow {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val cacheKey = "search|$q"
        searchCache.get(cacheKey)?.let {
            emit(Result.success(it))
        }

        try {
            searchCacheDao.getResults(q)?.let { cache ->
                val type = object : com.google.gson.reflect.TypeToken<List<Movie>>() {}.type
                val movies: List<Movie> = gson.fromJson(cache.resultsJson, type)
                if (movies.isNotEmpty()) {
                    searchCache.put(cacheKey, movies)
                    emit(Result.success(movies))
                    if (System.currentTimeMillis() - cache.timestamp < 3600000) return@flow
                }
            }
        } catch (_: Exception) {}

        try {
            val searchResults = searchSemaphore.withPermit {
                activeProvider.search(q, limit = 40)
            }
            
            val movies = searchResults.map { item ->
                Movie(
                    id = item.url.hashCode().toString(),
                    title = item.title,
                    poster = item.imageUrl,
                    year = item.year,
                    pageUrl = item.url,
                    type = item.type
                )
            }.distinctBy { it.title.lowercase().trim() }
            
            if (movies.isNotEmpty()) {
                searchCache.put(cacheKey, movies)
                emit(Result.success(movies))
                try {
                    searchCacheDao.insert(
                        ua.ukrtv.app.data.local.entity.SearchCacheEntity(
                            query = q,
                            resultsJson = gson.toJson(movies)
                        )
                    )
                } catch (_: Exception) {}
            } else {
                emit(Result.success(emptyList()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getDetails(id: String, url: String, categoryHint: ContentCategory?): Flow<Result<MovieDetail>> = flow {
        try {
            val providerName = activeProvider.name
            val cacheKey = "details_pure|$providerName|$url"
            
            navigationCache.get(cacheKey)?.let {
                emit(Result.success(it))
                return@flow
            }

            metadataCache.get(cacheKey)?.let {
                emit(Result.success(it))
                navigationCache.put(cacheKey, it)
                return@flow
            }

            if (url.isEmpty()) {
                emit(Result.failure(Exception("URL порожній")))
                return@flow
            }

            AppLogger.d("ContentRepository", "Fetching details directly from provider: $url")
            val detail = activeProvider.getMovieDetails(url)
            
            // Ensure seasons are loaded for series
            if (detail.contentType == ContentType.SERIES && detail.seasons.isNullOrEmpty()) {
                try {
                    val resolution = streamResolver.resolve(url)
                    if (resolution?.seasons != null) {
                        val updatedDetail = detail.copy(seasons = resolution.seasons)
                        metadataCache.put(cacheKey, updatedDetail)
                        navigationCache.put(cacheKey, updatedDetail)
                        emit(Result.success(updatedDetail))
                    } else {
                        emit(Result.success(detail))
                    }
                } catch (e: Exception) {
                    AppLogger.w("ContentRepository", "Season resolution failed: ${e.message}")
                    emit(Result.success(detail))
                }
            } else {
                metadataCache.put(cacheKey, detail)
                navigationCache.put(cacheKey, detail)
                emit(Result.success(detail))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ContentRepository", "Fatal error in getDetails: ${e.message}", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    suspend fun getStream(url: String, season: Int?, episode: Int?): StreamResolutionResult? {
        try {
            var res = streamResolver.resolve(url, season = season, episode = episode)
            if (res == null) {
                delay(1000)
                res = streamResolver.resolve(url, season = season, episode = episode)
            }
            return res
        } catch (e: Exception) {
            AppLogger.w("ContentRepository", "getStream failed: ${e.message}")
            return null
        }
    }
}
