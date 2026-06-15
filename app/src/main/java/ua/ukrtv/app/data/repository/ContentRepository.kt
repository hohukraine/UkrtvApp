package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.cache.TtlLruCache
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.repository.MediaRepository
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.toStreamResolutionResult
import ua.ukrtv.app.data.providers.toDomainSeason
import ua.ukrtv.app.util.ContentUtils
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ContentRepository @Inject constructor(
    private val providerManager: ProviderManager,
    private val watchProgressRepository: WatchProgressRepository,
    private val tmdbRepository: TmdbRepository
) : MediaRepository {
    private val activeProvider get() = providerManager.activeProvider.value


    override fun getHomeSections(): Flow<Result<List<HomeSection>>> = flow {
        try {
            // 1. Спочатку видаємо "сирі" дані від провайдера миттєво
            val rawSections = activeProvider.getHomeSections()
            
            if (rawSections.isEmpty() || rawSections.all { it.items.isEmpty() }) {
                AppLogger.w("ContentRepository", "Provider returned empty sections, falling back to TMDB")
                val trending = tmdbRepository.getTrending()
                emit(Result.success(listOf(HomeSection("В тренді (TMDB)", trending))))
                return@flow
            }

            emit(Result.success(rawSections))

            // 2. Потім асинхронно збагачуємо їх даними TMDB, не блокуючи UI
            val enrichedSections = rawSections.map { section ->
                coroutineScope {
                    val enrichedItems = section.items.map { movie ->
                        async { tmdbRepository.enrichMovie(movie) }
                    }.awaitAll()
                    section.copy(items = enrichedItems)
                }
            }
            emit(Result.success(enrichedSections))
        } catch (e: Exception) {
            AppLogger.e("ContentRepository", "Home sections failed", e)
            val trending = tmdbRepository.getTrending()
            emit(Result.success(listOf(HomeSection("В тренді (TMDB)", trending))))
        }
    }.flowOn(Dispatchers.IO)

    override fun getContinueWatching(): Flow<List<Movie>> = flow {
        val allProgress = watchProgressRepository.getAllProgress()
        val movies = allProgress
            .filter { it.progressPercentage in 1..94 } // Only ongoing
            .map { progress ->
                val pUrl = progress.pageUrl
                val rawTitle = progress.title.orEmpty()
                val cleanTitle = rawTitle.replace("+", " ").replace("_", " ").trim()
                
                Movie(
                    id = progress.contentId,
                    title = cleanTitle,
                    poster = progress.poster.orEmpty(),
                    year = null,
                    pageUrl = pUrl,
                    type = ContentUtils.inferContentType(pUrl, rawTitle),
                    watchProgress = progress.progressPercentage
                )
            }
        emit(movies)
    }.flowOn(Dispatchers.IO)

    override fun getBannerMovie(): Flow<Movie?> = flow<Movie?> {
        try {
            val sections = activeProvider.getHomeSections()
            val movie = sections.flatMap { it.items }.randomOrNull()
            if (movie != null) {
                emit(tmdbRepository.enrichMovie(movie))
            } else {
                emit(tmdbRepository.getTrending().randomOrNull())
            }
        } catch (e: Exception) {
            emit(tmdbRepository.getTrending().randomOrNull())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPopularByCategory(category: ContentCategory): Flow<List<Movie>> = flow<List<Movie>> {
        try {
            val movies = activeProvider.getPopular(category)
            coroutineScope {
                val toEnrich = movies.take(15)
                val others = movies.drop(15)
                val enriched = toEnrich.map { async { tmdbRepository.enrichMovie(it) } }.awaitAll()
                emit(enriched + others)
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private val searchCache = TtlLruCache<String, List<Movie>>(maxSize = 50, ttlMs = Constants.SEARCH_CACHE_TTL_MS)
    private val metadataCache = TtlLruCache<String, MovieDetail>(maxSize = 200, ttlMs = Constants.METADATA_CACHE_TTL_MS)
    private val playlistCache = TtlLruCache<String, MediaSource>(maxSize = 100, ttlMs = Constants.PLAYLIST_CACHE_TTL_MS)
    private val navigationCache = java.util.concurrent.ConcurrentHashMap<String, MovieDetail>()

    override fun search(query: String): Flow<Result<List<Movie>>> = flow {
        try {
            val cacheKey = "search|tmdb|${query.trim()}"
            val cached = searchCache.get(cacheKey)
            if (cached != null) {
                emit(Result.success(cached))
                return@flow
            }

            val movies = tmdbRepository.search(query)
            searchCache.put(cacheKey, movies)
            emit(Result.success(movies))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getDetails(id: String, url: String): Flow<Result<MovieDetail>> = flow<Result<MovieDetail>> {
        try {
            val providerName = providerManager.activeProvider.value.javaClass.simpleName
            val cacheKey = "details|$providerName|$id|$url"
            
            // Миттєвий результат з пам'яті (Sync)
            navigationCache[cacheKey]?.let {
                emit(Result.success(it))
            }

            val cached = metadataCache.get(cacheKey)
            if (cached != null) {
                if (navigationCache[cacheKey] == null) {
                    emit(Result.success(cached))
                    navigationCache[cacheKey] = cached
                }
                return@flow
            }

            var detail: MovieDetail? = null

            if (id.startsWith("tmdb|") || url.isEmpty()) {
                val tmdbDetail = if (id.startsWith("tmdb|")) {
                    tmdbRepository.getDetailsById(id)
                } else null

                if (tmdbDetail != null) {
                    val searchResults = activeProvider.search(tmdbDetail.title, limit = 20)
                    
                    // 1. Шукаємо точний збіг по року та типу
                    var match = searchResults.firstOrNull { s ->
                        val sTitle = ContentUtils.cleanTitle(s.title).lowercase()
                        val tTitle = tmdbDetail.title.lowercase()
                        val yearsMatch = s.year != null && tmdbDetail.year != null && s.year.take(4) == tmdbDetail.year.take(4)
                        val typeMatch = s.type == tmdbDetail.contentType
                        yearsMatch && typeMatch && (sTitle == tTitle || sTitle.contains(tTitle) || tTitle.contains(sTitle))
                    }
                    
                    // 2. Якщо не знайдено, пробуємо без жорсткої перевірки типу, але з роком
                    if (match == null) {
                        match = searchResults.firstOrNull { s ->
                            val sTitle = ContentUtils.cleanTitle(s.title).lowercase()
                            val tTitle = tmdbDetail.title.lowercase()
                            val yearsMatch = s.year != null && tmdbDetail.year != null && s.year.take(4) == tmdbDetail.year.take(4)
                            yearsMatch && (sTitle.contains(tTitle) || tTitle.contains(sTitle))
                        }
                    }
                    
                    // 3. Крайній випадок: просто по назві, але фільтруємо "сміття"
                    if (match == null) {
                        match = searchResults.firstOrNull { s ->
                            val sTitle = ContentUtils.cleanTitle(s.title).lowercase()
                            val tTitle = tmdbDetail.title.lowercase()
                            (sTitle == tTitle || sTitle.startsWith(tTitle) || tTitle.startsWith(sTitle))
                        }
                    }
                    
                    if (match != null) {
                        // Для серіалів: отримуємо реальні епізоди з провайдера
                        var seasonsWithEpisodes: List<Season>? = null
                        if (tmdbDetail.contentType == ContentType.SERIES) {
                            try {
                                val mediaSource = activeProvider.getMediaSource(match.url)
                                if (mediaSource is MediaSource.Series) {
                                    seasonsWithEpisodes = mediaSource.seasons.map { it.toDomainSeason() }
                                }
                            } catch (_: Exception) {
                                // Keep tmdbDetail.seasons as skeleton if provider fails
                            }
                        }
                        
                        detail = tmdbDetail.copy(
                            pageUrl = match.url,
                            providerName = providerName,
                            seasons = seasonsWithEpisodes ?: tmdbDetail.seasons
                        )
                    } else {
                        detail = tmdbDetail
                    }
                }
            }

            if (detail == null && url.isNotEmpty()) {
                // Якщо TMDB не знайшов, тоді (і тільки тоді) використовуємо провайдер як основне джерело
                detail = activeProvider.getDetails(id, url)
                detail = tmdbRepository.enrichDetails(detail)
            }

            if (detail == null) {
                emit(Result.failure(Exception("Контент не знайдено")))
            } else {
                metadataCache.put(cacheKey, detail)
                navigationCache[cacheKey] = detail
                emit(Result.success(detail))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    override suspend fun getStream(url: String): StreamResolutionResult? = withContext(Dispatchers.IO) {
        val providerName = activeProvider.javaClass.simpleName
        val cacheKey = "playlist|$providerName|$url"

        playlistCache.get(cacheKey)?.let { cached ->
            return@withContext cached.toStreamResolutionResult(providerName = providerName, sourcePageUrl = url)
        }

        val fresh = activeProvider.getMediaSource(url)
        if (fresh != null) {
            playlistCache.put(cacheKey, fresh)
            return@withContext fresh.toStreamResolutionResult(providerName = providerName, sourcePageUrl = url)
        }
        null
    }
}