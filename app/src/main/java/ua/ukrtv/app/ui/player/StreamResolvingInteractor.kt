package ua.ukrtv.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.matching.SearchScorer

sealed class StreamResolutionException(message: String) : Exception(message) {
    class Network(message: String) : StreamResolutionException(message)
    class Blocked(message: String) : StreamResolutionException(message)
    class NotFound(message: String) : StreamResolutionException(message)
}

class StreamResolvingInteractor(
    private val streamResolver: StreamResolver,
    private val providerManager: ProviderManager
) {
    suspend fun resolve(
        url: String,
        title: String,
        season: Int?,
        episode: Int?,
        voiceover: String?,
        isDeep: Boolean = false
    ): StreamResolutionResult? = withContext(Dispatchers.IO) {
        try {
            val res = streamResolver.resolve(url, season = season, episode = episode, voiceover = voiceover, isDeep = isDeep)
            res ?: searchAndResolveOnAlternateProvider(url, title, season, episode, voiceover)
        } catch (e: Exception) {
            AppLogger.e("StreamResolvingInteractor", "Resolve failed", e)
            when {
                e is java.net.SocketTimeoutException || e.message?.contains("timeout", true) == true ->
                    throw StreamResolutionException.Network("Мережева помилка: перевірте з'єднання")
                e.message?.contains("403") == true || e.message?.contains("429") == true ->
                    throw StreamResolutionException.Blocked("Контент тимчасово недоступний")
                e.message?.contains("404") == true ->
                    throw StreamResolutionException.NotFound("Відео не знайдено")
                else -> null  // невідомі помилки — fallback до "не знайдено"
            }
        }
    }

    suspend fun searchAndResolveOnAlternateProvider(
        originalUrl: String,
        title: String,
        season: Int?,
        episode: Int?,
        voiceover: String?
    ): StreamResolutionResult? {
        val other = providerManager.getOppositeProvider(originalUrl) ?: return null
        return try {
            val query = SearchScorer.cleanSearchQuery(title)
            val results = other.search(query, limit = 10)

            // Loose list scans can return entries whose title is paired with a neighbour
            // card's URL (title↔slug mismatch). Never resolve such candidates for playback.
            val candidates = results.mapNotNull { item ->
                if (SearchScorer.titleSlugConsistency(item.title, item.url) < MIN_SLUG_CONSISTENCY) {
                    AppLogger.w("StreamResolvingInteractor", "Rejected mismatched candidate '${item.title}' @ ${item.url}")
                    null
                } else {
                    Movie(
                        id = item.url,
                        title = item.title,
                        poster = item.imageUrl,
                        pageUrl = item.url,
                        year = item.year?.toIntOrNull(),
                        provider = item.provider
                    )
                }
            }

            val match = SearchScorer.pickBestMatch(candidates, listOf(query)) ?: run {
                AppLogger.w("StreamResolvingInteractor", "No confident cross-provider match for '$title' on ${other.name}; skipping playback")
                return null
            }

            AppLogger.d("StreamResolvingInteractor", "Cross-provider match: '${match.title}' @ ${match.pageUrl}")
            streamResolver.resolve(match.pageUrl, season = season, episode = episode, voiceover = voiceover, isDeep = false)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val MIN_SLUG_CONSISTENCY = 0.35f
    }
}
