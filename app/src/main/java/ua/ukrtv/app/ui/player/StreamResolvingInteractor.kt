package ua.ukrtv.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.player.ProviderQualityManager
import ua.ukrtv.app.util.AppLogger

sealed class StreamResolutionException(message: String) : Exception(message) {
    class Network(message: String) : StreamResolutionException(message)
    class Blocked(message: String) : StreamResolutionException(message)
    class NotFound(message: String) : StreamResolutionException(message)
}

class StreamResolvingInteractor(
    private val streamResolver: StreamResolver,
    private val providerManager: ProviderManager,
    private val providerQualityManager: ProviderQualityManager
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
            if (res != null) {
                val bestRes = resolveWithBestProvider(url, title, res, season, episode, voiceover)
                bestRes ?: res
            } else {
                searchAndResolveOnAlternateProvider(url, title, season, episode, voiceover)
            }
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

    private suspend fun resolveWithBestProvider(
        originalUrl: String,
        title: String,
        primaryResult: StreamResolutionResult,
        season: Int?,
        episode: Int?,
        voiceover: String?
    ): StreamResolutionResult? {
        return try {
            val otherProvider = providerManager.getOppositeProvider(originalUrl) ?: return null
            val primaryName = providerManager.activeProvider.value.name

            val cachedPrimary = providerQualityManager.getScore(primaryName)
            val cachedOther = providerQualityManager.getScore(otherProvider.name)
            
            if (cachedPrimary != null && cachedOther != null) {
                val cachedDecision = providerQualityManager.selectBest(
                    providerQualityManager.toSpeedTestResult(cachedPrimary),
                    providerQualityManager.toSpeedTestResult(cachedOther)
                )
                if (cachedDecision != null && cachedDecision.providerName != primaryName) {
                    AppLogger.d("StreamResolvingInteractor", "Using faster provider (cached): ${cachedDecision.providerName}")
                    val results = otherProvider.search(title, limit = 5)
                    val match = results.firstOrNull() ?: return null
                    return streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false)
                }
                return null
            }

            val primaryTest = providerQualityManager.testSpeed(primaryName, primaryResult.streamUrl, primaryResult.referer)
            if (primaryTest != null) providerQualityManager.recordScore(primaryName, primaryTest)

            val results = otherProvider.search(title, limit = 5)
            val match = results.firstOrNull() ?: return null
            val otherRes = streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false) ?: return null

            val otherTest = providerQualityManager.testSpeed(otherProvider.name, otherRes.streamUrl, otherRes.referer)
            if (otherTest != null) providerQualityManager.recordScore(otherProvider.name, otherTest)

            val best = providerQualityManager.selectBest(primaryTest, otherTest)
            if (best != null && best.providerName != primaryName) {
                otherRes
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
            val results = other.search(title, limit = 5)
            val match = results.firstOrNull() ?: return null
            streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false)
        } catch (e: Exception) {
            null
        }
    }
}
