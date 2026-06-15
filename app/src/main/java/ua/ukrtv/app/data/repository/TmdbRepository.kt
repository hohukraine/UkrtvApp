package ua.ukrtv.app.data.repository

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ua.ukrtv.app.data.api.TmdbApi
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Episode
import javax.inject.Inject
import javax.inject.Singleton

import ua.ukrtv.app.util.ContentUtils

@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbApi: TmdbApi
) {
    private val apiKey = "6c6c422402d3cbb653d6f81558a2c899"
    private val enrichCache = ua.ukrtv.app.data.cache.TtlLruCache<String, Movie>(maxSize = 500, ttlMs = 86400000)
    
    // Обмежуємо паралелізм, щоб не "покласти" слабкі пристрої при масовому збагаченні
    private val enrichmentSemaphore = kotlinx.coroutines.sync.Semaphore(5)

    suspend fun getTrending(): List<Movie> = try {
        tmdbApi.getTrendingAll(apiKey).results.map { it.toMovie() }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun search(query: String): List<Movie> = try {
        tmdbApi.searchMulti(apiKey, query).results.map { it.toMovie() }
    } catch (e: Exception) {
        emptyList()
    }
    
    // ... (інші методи)

    suspend fun enrichMovie(movie: Movie): Movie = enrichmentSemaphore.withPermit {
        val cleanInputTitle = ContentUtils.cleanTitle(movie.title)
        
        val cacheKey = "enrich|$cleanInputTitle|${movie.year}"
        enrichCache.get(cacheKey)?.let { return@withPermit it.copy(pageUrl = movie.pageUrl, id = movie.id) }
        
        try {
            var searchResponse = tmdbApi.searchMulti(
                apiKey = apiKey,
                query = cleanInputTitle,
                year = movie.year?.filter { it.isDigit() }?.take(4)
            )
            
            // Якщо нічого не знайшли з роком, пробуємо без року
            if (searchResponse.results.isEmpty() && !movie.year.isNullOrBlank()) {
                searchResponse = tmdbApi.searchMulti(
                    apiKey = apiKey,
                    query = cleanInputTitle,
                    year = null
                )
            }
            
            val queryTitle = cleanInputTitle.lowercase()
            val year = movie.year?.filter { it.isDigit() }?.take(4)

            fun normalize(s: String?): String = s?.trim()?.lowercase().orEmpty()
            fun titleScore(candidate: String?): Int {
                val c = normalize(candidate)
                if (c.isEmpty() || queryTitle.isEmpty()) return 0
                return if (c == queryTitle) 100
                else if (c.contains(queryTitle) || queryTitle.contains(c)) 70
                else 0
            }

            fun yearScore(candidateYear: String?): Int {
                if (year.isNullOrBlank() || candidateYear.isNullOrBlank()) return 0
                val cy = candidateYear.filter { it.isDigit() }.take(4)
                return if (cy == year) 60 else 0
            }

            val match = searchResponse.results
                .map { r ->
                    val candidateTitle = r.name ?: r.title
                    val score = titleScore(candidateTitle) + yearScore(r.releaseDate ?: r.firstAirDate)
                    r to score
                }
                .sortedByDescending { it.second }
                .filter { it.second > 50 } // Мінімальний поріг впевненості
                .firstOrNull()?.first

            val enriched = if (match != null) {
                val type = if (match.mediaType == "tv") ContentType.SERIES else ContentType.MOVIE
                movie.copy(
                    title = match.title ?: match.name ?: cleanInputTitle,
                    poster = if (match.posterPath != null) "https://image.tmdb.org/t/p/w500${match.posterPath}" else movie.poster,
                    shortDescription = match.overview ?: movie.shortDescription,
                    type = type
                )
            } else {
                movie.copy(title = cleanInputTitle)
            }
            enrichCache.put(cacheKey, enriched)
            enriched
        } catch (e: Exception) {
            movie.copy(title = cleanInputTitle)
        }
    }

    private fun ua.ukrtv.app.data.api.TmdbSearchResult.toMovie(): Movie {
        val type = if (mediaType == "tv" || firstAirDate != null) ContentType.SERIES else ContentType.MOVIE
        val mType = mediaType ?: if (type == ContentType.SERIES) "tv" else "movie"
        return Movie(
            id = "tmdb|$mType|$id",
            title = title ?: name ?: "",
            poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else "",
            year = (releaseDate ?: firstAirDate)?.take(4),
            type = type,
            pageUrl = "", // Буде визначено при переході на деталі
            shortDescription = overview
        )
    }

    suspend fun getDetailsById(idStr: String): MovieDetail? = try {
        val parts = idStr.split("|")
        val mediaType = parts.getOrNull(1) ?: "movie"
        val tmdbId = parts.getOrNull(2)?.toInt() ?: 0
        
        if (mediaType == "tv") {
            val d = tmdbApi.getTvDetails(tmdbId, apiKey)
            
            // Заповнюємо базову структуру сезонів
            val seasons = (1..(d.numberOfSeasons ?: 0)).map { sNum ->
                Season(sNum, emptyList<Episode>())
            }

            MovieDetail(
                id = idStr,
                title = d.name,
                poster = "https://image.tmdb.org/t/p/w500${d.posterPath}",
                description = d.overview.orEmpty(),
                year = d.firstAirDate?.take(4),
                genres = emptyList(),
                pageUrl = "",
                providerName = "TMDB",
                seasons = seasons,
                streamUrl = null,
                contentType = ContentType.SERIES,
                seasonCount = d.numberOfSeasons,
                episodeCount = d.numberOfEpisodes
            )
        } else {
            val d = tmdbApi.getMovieDetails(tmdbId, apiKey)
            MovieDetail(
                id = idStr,
                title = d.title,
                poster = "https://image.tmdb.org/t/p/w500${d.posterPath}",
                description = d.overview.orEmpty(),
                year = d.releaseDate?.take(4),
                genres = emptyList(),
                pageUrl = "",
                providerName = "TMDB",
                seasons = null,
                streamUrl = null,
                contentType = ContentType.MOVIE
            )
        }
    } catch (e: Exception) {
        null
    }

    suspend fun enrichDetails(details: MovieDetail): MovieDetail {
        return try {
            val cleanTitle = ContentUtils.cleanTitle(details.title)
            var searchResponse = tmdbApi.searchMulti(
                apiKey = apiKey,
                query = cleanTitle,
                year = details.year?.filter { it.isDigit() }?.take(4)
            )
            
            if (searchResponse.results.isEmpty() && !details.year.isNullOrBlank()) {
                searchResponse = tmdbApi.searchMulti(
                    apiKey = apiKey,
                    query = cleanTitle,
                    year = null
                )
            }

            val queryTitle = cleanTitle.lowercase()
            val year = details.year?.filter { it.isDigit() }?.take(4)

            fun normalize(s: String?): String = s?.trim()?.lowercase().orEmpty()
            fun titleScore(candidate: String?): Int {
                val c = normalize(candidate)
                if (c.isEmpty() || queryTitle.isEmpty()) return 0
                return if (c == queryTitle) 100
                else if (c.contains(queryTitle) || queryTitle.contains(c)) 70
                else 0
            }

            fun yearScore(candidateYear: String?): Int {
                if (year.isNullOrBlank() || candidateYear.isNullOrBlank()) return 0
                val cy = candidateYear.filter { it.isDigit() }.take(4)
                return if (cy == year) 60 else 0
            }

            val match = searchResponse.results
                .map { r ->
                    val candidateTitle = r.name ?: r.title
                    val score = titleScore(candidateTitle) + yearScore(r.releaseDate ?: r.firstAirDate)
                    // Додатковий бонус за правильний тип, якщо він визначений провайдером
                    val typeBonus = if (
                        (r.mediaType == "tv" && details.contentType == ContentType.SERIES) ||
                        (r.mediaType == "movie" && details.contentType == ContentType.MOVIE)
                    ) 30 else 0
                    Triple(r, score + typeBonus, r.mediaType)
                }
                .sortedByDescending { it.second }
                .filter { it.second > 60 } // Підвищуємо поріг для деталей
                .firstOrNull()
                ?.first

            if (match != null) {
                if (match.mediaType == "tv") {
                    val tvDetails = tmdbApi.getTvDetails(match.id, apiKey)
                    details.copy(
                        title = cleanTitle,
                        poster = if (details.poster.isBlank() || details.poster.contains("placeholder")) "https://image.tmdb.org/t/p/w500${tvDetails.posterPath}" else details.poster,
                        description = if (details.description.isBlank()) tvDetails.overview.orEmpty() else details.description,
                        seasonCount = tvDetails.numberOfSeasons,
                        episodeCount = tvDetails.numberOfEpisodes,
                        contentType = ContentType.SERIES
                    )
                } else {
                    val movieDetails = tmdbApi.getMovieDetails(match.id, apiKey)
                    details.copy(
                        title = cleanTitle,
                        poster = if (details.poster.isBlank() || details.poster.contains("placeholder")) "https://image.tmdb.org/t/p/w500${movieDetails.posterPath}" else details.poster,
                        description = if (details.description.isBlank()) movieDetails.overview.orEmpty() else details.description,
                        contentType = ContentType.MOVIE
                    )
                }
            } else {
                details.copy(title = cleanTitle)
            }
        } catch (e: Exception) {
            details.copy(title = ContentUtils.cleanTitle(details.title))
        }
    }
}
