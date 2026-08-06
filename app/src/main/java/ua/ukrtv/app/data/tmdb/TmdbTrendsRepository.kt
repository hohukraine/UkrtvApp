package ua.ukrtv.app.data.tmdb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ua.ukrtv.app.BuildConfig
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.local.dao.TmdbTrendsCacheDao
import ua.ukrtv.app.data.local.entity.TmdbTrendsCacheEntity
import ua.ukrtv.app.data.providers.MediaProvider
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.matching.SearchScorer
import ua.ukrtv.app.matching.TrendsSeedFile
import ua.ukrtv.app.matching.TrendsSeedItem
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbTrendsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbClient: TmdbClient,
    private val catalogRepository: CatalogRepository,
    private val tmdbTrendsCacheDao: TmdbTrendsCacheDao,
    private val json: Json
) {
    private val cache = TtlLruCache<String, List<Movie>>(maxSize = 6, ttlMs = TRENDS_FRESH_MS)
    private val matchSemaphore = Semaphore(4)
    private val seedMutex = Mutex()
    private val seeded = mutableSetOf<String>()

    suspend fun getTrends(provider: MediaProvider, forceRefresh: Boolean = false): List<Movie> {
        if (BuildConfig.TMDB_API_KEY.isBlank()) return emptyList()

        val cacheKey = provider.name
        seedIfNeeded(cacheKey)
        if (!forceRefresh) cache.get(cacheKey)?.let { return it }
        if (!forceRefresh) {
            val persisted = readPersisted(cacheKey)
            if (persisted != null && isFresh(persisted.cachedAt)) {
                val movies = decodeMovies(persisted.moviesJson)
                if (movies.isNotEmpty()) {
                    cache.put(cacheKey, movies)
                    return movies
                }
            }
        }

        try {
            val result = withContext(Dispatchers.IO) { buildTrends(provider) }
            if (result.movies.isNotEmpty()) {
                cache.put(cacheKey, result.movies)
                persist(cacheKey, result.movies, result.itemIds)
            }
            return result.movies
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("TmdbTrends", "Failed to build trends for ${provider.name}: ${e.message}")
            // Serve stale persisted data as a fallback instead of failing hard
            readPersisted(cacheKey)?.let { entity ->
                val movies = decodeMovies(entity.moviesJson)
                if (movies.isNotEmpty()) {
                    cache.put(cacheKey, movies)
                    return movies
                }
            }
            throw e
        }
    }

    /**
     * Fast path used to render home trends instantly on cold start (stale-while-revalidate):
     * in-memory or Room cache of any reasonable age, no network, no catalog matching.
     */
    suspend fun getCachedTrends(provider: MediaProvider): List<Movie> {
        if (BuildConfig.TMDB_API_KEY.isBlank()) return emptyList()
        val cacheKey = provider.name
        seedIfNeeded(cacheKey)
        cache.get(cacheKey)?.let { return it }
        val entity = readPersisted(cacheKey) ?: return emptyList()
        if (System.currentTimeMillis() - entity.cachedAt > TRENDS_SERVE_STALE_MAX_MS) return emptyList()
        val movies = decodeMovies(entity.moviesJson)
        if (movies.isNotEmpty()) cache.put(cacheKey, movies)
        return movies
    }

    fun clearCache() {
        cache.clear()
        tmdbClient.clearCache()
    }

    /**
     * Seeds the bundled trends_index.json (generated at build time) into the cache on first
     * launch or when the seed is newer than what we already have, so home trends render
     * instantly without any matching. Everything afterwards stays incremental.
     */
    private suspend fun seedIfNeeded(cacheKey: String) {
        if (cacheKey in seeded) return
        seedMutex.withLock {
            if (cacheKey in seeded) return
            seeded.add(cacheKey)
            try {
                val existing = readPersisted(cacheKey)
                val seed = withContext(Dispatchers.IO) {
                    context.assets.open(SEED_ASSET).use { input ->
                        json.decodeFromString<TrendsSeedFile>(input.readBytes().decodeToString())
                    }
                }
                val providerSeed = seed.providers.firstOrNull { it.provider == cacheKey } ?: return
                if (providerSeed.items.isEmpty()) return
                if (existing != null && existing.cachedAt >= seed.generatedAt) return

                val movies = providerSeed.items.map { it.toMovie() }
                tmdbTrendsCacheDao.put(
                    TmdbTrendsCacheEntity(
                        provider = cacheKey,
                        moviesJson = json.encodeToString(movies),
                        itemIdsJson = json.encodeToString(providerSeed.items.map { it.tmdbId }),
                        cachedAt = seed.generatedAt
                    )
                )
                cache.put(cacheKey, movies)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("TmdbTrends", "Seed failed for $cacheKey: ${e.message}")
            }
        }
    }

    private fun TrendsSeedItem.toMovie() = Movie(
        id = url,
        title = title,
        poster = poster,
        pageUrl = url,
        provider = provider,
        rating = rating.ifEmpty { null },
        year = year.toIntOrNull(),
        quality = quality.ifEmpty { null },
        contentType = contentType.ifEmpty { null }
    )

    /**
     * Fetches the current TMDB set, then only re-matches items that are not already
     * matched in the persisted cache: existing matches are kept as-is, new ones are
     * added and items that rolled out of the trend window are dropped.
     */
    private suspend fun buildTrends(provider: MediaProvider): MatchedTrends {
        val items = coroutineScope {
            val trending = async(Dispatchers.IO) { (1..2).flatMap { page -> tmdbClient.trending(page) } }
            val popular = async(Dispatchers.IO) { tmdbClient.popularMovies(1) + tmdbClient.popularTv(1) }
            (trending.await() + popular.await())
                .distinctBy { it.id }
        }
        if (items.isEmpty()) return MatchedTrends(emptyList(), emptyList())

        catalogRepository.awaitReady()

        val previous = readPersisted(provider.name)
        // A very old base forces a full re-match so catalog changes (titles/posters/
        // ratings) propagate instead of being kept stale forever.
        val previousUsable = previous != null &&
            System.currentTimeMillis() - previous.cachedAt < FULL_REMATCH_MS

        val previousIds = previous?.let { decodeIds(it.itemIdsJson).toSet() }.orEmpty()
        val newItems = if (previousUsable) items.filter { it.id !in previousIds } else items

        val newlyMatched = matchItems(provider, newItems)

        val previousById = if (previousUsable && previous != null) {
            val ids = decodeIds(previous.itemIdsJson)
            val movies = decodeMovies(previous.moviesJson)
            ids.zip(movies).toMap()
        } else {
            emptyMap()
        }

        val candidates = items.mapNotNull { item ->
            val movie = newlyMatched[item.id] ?: previousById[item.id]
            movie?.let { item.id to it }
        }
        val seen = mutableSetOf<String>()
        val finalPairs = candidates.filter { (_, movie) -> seen.add(movie.pageUrl) }
        return MatchedTrends(
            movies = finalPairs.map { it.second },
            itemIds = finalPairs.map { it.first }
        )
    }

    private suspend fun matchItems(provider: MediaProvider, items: List<TmdbTrendingItem>): Map<Long, Movie> {
        if (items.isEmpty()) return emptyMap()
        val matched = coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) {
                    matchSemaphore.withPermit {
                        try {
                            findMatch(provider, item)?.let { item.id to it }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.w("TmdbTrends", "Match failed for '${item.displayTitle}': ${e.message}")
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        return matched.filterNotNull().toMap()
    }

    private suspend fun findMatch(provider: MediaProvider, item: TmdbTrendingItem): Movie? {
        val english = item.originalTitleValue
        val localized = item.displayTitle
        val queries = listOf(english, localized)
            .map { SearchScorer.normalizeTitle(it) }
            .filter { it.length >= 2 }
            .distinct()
        if (queries.isEmpty()) return null

        val candidates = mutableListOf<Movie>()
        val seen = mutableSetOf<String>()
        for (q in queries) {
            val exact = catalogRepository.searchByProviderWordsExact(provider.name, q, limit = 20)
            val entities = if (exact.isNotEmpty()) exact else catalogRepository.searchByProviderWords(provider.name, q, limit = 20)
            for (entity in entities) {
                if (seen.add(entity.url)) {
                    candidates.add(
                        Movie(
                            id = entity.url,
                            title = entity.title,
                            poster = entity.poster,
                            pageUrl = entity.url,
                            provider = entity.provider,
                            rating = entity.rating.ifEmpty { null },
                            year = entity.year.toIntOrNull(),
                            quality = entity.quality.ifEmpty { null },
                            contentType = entity.contentType.ifEmpty { null }
                        )
                    )
                }
            }
        }
        if (candidates.isEmpty()) return null

        val matchQueries = listOf(english, localized)
            .mapNotNull { SearchScorer.cleanSearchQuery(it).takeIf { s -> s.isNotEmpty() } }
            .ifEmpty { queries }
        val best = SearchScorer.pickBestMatch(candidates, matchQueries, item.year) ?: return null
        return candidates.firstOrNull { it.pageUrl == best.pageUrl }
    }

    private data class MatchedTrends(
        val movies: List<Movie>,
        val itemIds: List<Long>
    )

    private suspend fun readPersisted(provider: String): TmdbTrendsCacheEntity? = withContext(Dispatchers.IO) {
        try { tmdbTrendsCacheDao.get(provider) } catch (_: Exception) { null }
    }

    private suspend fun persist(provider: String, movies: List<Movie>, itemIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            tmdbTrendsCacheDao.put(
                TmdbTrendsCacheEntity(
                    provider = provider,
                    moviesJson = json.encodeToString(movies),
                    itemIdsJson = json.encodeToString(itemIds),
                    cachedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) { }
    }

    private fun decodeMovies(raw: String): List<Movie> = try {
        json.decodeFromString<List<Movie>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun decodeIds(raw: String): List<Long> = try {
        json.decodeFromString<List<Long>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun isFresh(cachedAt: Long): Boolean = System.currentTimeMillis() - cachedAt < TRENDS_FRESH_MS

    companion object {
        private const val TRENDS_FRESH_MS = 6 * 60 * 60 * 1000L
        private const val TRENDS_SERVE_STALE_MAX_MS = 7 * 24 * 60 * 60 * 1000L
        private const val FULL_REMATCH_MS = 7 * 24 * 60 * 60 * 1000L
        private const val SEED_ASSET = "trends_index.json"
    }
}
