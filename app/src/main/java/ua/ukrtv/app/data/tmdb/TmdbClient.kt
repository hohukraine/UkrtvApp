package ua.ukrtv.app.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.BuildConfig
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.util.AppLogger

class TmdbClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val pageCache = TtlLruCache<String, List<TmdbTrendingItem>>(maxSize = 12, ttlMs = 6 * 60 * 60 * 1000L)

    suspend fun trending(page: Int = 1): List<TmdbTrendingItem> =
        fetch("trending/all/week", page)

    suspend fun popularMovies(page: Int = 1): List<TmdbTrendingItem> =
        fetch("movie/popular", page, mediaType = "movie")

    suspend fun popularTv(page: Int = 1): List<TmdbTrendingItem> =
        fetch("tv/popular", page, mediaType = "tv")

    private suspend fun fetch(path: String, page: Int, mediaType: String? = null): List<TmdbTrendingItem> {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) return emptyList()

        val cacheKey = "$path|$page"
        pageCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.themoviedb.org/3/$path?api_key=$apiKey&language=uk-UA&page=$page"
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        throw Exception("TMDB error ${response.code} for $path")
                    }
                    val parsed = json.decodeFromString<TmdbTrendingResponse>(body)
                    val items = if (mediaType != null) {
                        parsed.results.map { it.copy(mediaType = mediaType) }
                    } else {
                        parsed.results
                    }
                    pageCache.put(cacheKey, items)
                    items
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("TmdbClient", "Failed to load $path page $page: ${e.message}")
                throw e
            }
        }
    }

    fun clearCache() {
        pageCache.clear()
    }
}
