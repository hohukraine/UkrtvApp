package ua.ukrtv.app.data.streaming

import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.data.streaming.strategies.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolver @Inject constructor(
    private val streamManager: ua.ukrtv.app.data.providers.StreamManager,
    private val resolutionLogger: ua.ukrtv.app.util.ResolutionLogger,
    directLinkStrategy: DirectLinkStrategy,
    providerStrategy: ProviderResolutionStrategy,
    iframeStrategy: IframeResolutionStrategy
) {

    private val streamResolutionCache = ua.ukrtv.app.data.TtlLruCache<String, StreamResolutionResult?>(
        maxSize = 100,
        ttlMs = 2 * 60 * 1000L
    )

    private val inflightMutexes = ConcurrentHashMap<String, Mutex>()

    private val resolutionChain = ResolutionChain(
        strategies = listOf(
            directLinkStrategy,
            providerStrategy,
            iframeStrategy
        ),
        logger = resolutionLogger
    )

    suspend fun resolve(
        url: String,
        referer: String = "",
        season: Int? = null,
        episode: Int? = null,
        voiceover: String? = null,
        isDeep: Boolean = true,
        prefetchedHtml: String? = null
    ): StreamResolutionResult? {
        val sectionName = "StreamResolver.resolve${if (isDeep) " (deep)" else if (season != null) " S${season}E${episode}" else ""}"
        PerformanceMonitor.begin(sectionName)
        try {
            return withTimeout(ua.ukrtv.app.Constants.STREAM_RESOLUTION_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val cacheKey = "resolve|$url|$referer|$season|$episode|$voiceover|$isDeep"

                    streamResolutionCache.get(cacheKey)?.let { cached ->
                        AppLogger.d("StreamResolver", "Cache hit for $url")
                        return@withContext cached
                    }

                    val mutex = inflightMutexes.computeIfAbsent(cacheKey) { Mutex() }
                    try {
                        mutex.withLock {
                            streamResolutionCache.get(cacheKey)?.let { cached ->
                                AppLogger.d("StreamResolver", "Cache hit (after lock) for $url")
                                return@withContext cached
                            }

                            if (isForbiddenUrl(url)) {
                                AppLogger.w("StreamResolver", "URL is forbidden: $url")
                                return@withLock null
                            }

                            val context = ResolutionContext(
                                season = season,
                                episode = episode,
                                voiceover = voiceover,
                                isDeep = isDeep,
                                referer = referer,
                                prefetchedHtml = prefetchedHtml
                            )

                            var currentResult = resolutionChain.resolve(url, context)
                            
                            // Recursive resolution for iframes/redirects
                            var attempts = 0
                            while (currentResult != null && !isDirectStreamUrl(currentResult.streamUrl) && attempts < 3) {
                                val nextUrl = currentResult.streamUrl
                                if (nextUrl == url) break
                                
                                AppLogger.d("StreamResolver", "Recursive resolution for: $nextUrl")
                                val nextContext = context.copy(referer = currentResult.referer)
                                val nextResult = resolutionChain.resolve(nextUrl, nextContext)
                                
                                if (nextResult == null || nextResult.streamUrl == nextUrl) break
                                
                                currentResult = nextResult.copy(
                                    seasons = currentResult.seasons ?: nextResult.seasons,
                                    providerName = currentResult.providerName.ifEmpty { nextResult.providerName },
                                    sourcePageUrl = currentResult.sourcePageUrl
                                )
                                attempts++
                            }
                            
                            if (currentResult != null) {
                                streamResolutionCache.put(cacheKey, currentResult)
                            }
                            
                            return@withLock currentResult
                        }
                    } finally {
                        inflightMutexes.remove(cacheKey)
                    }
                }
            }
        } finally {
            PerformanceMonitor.end()
        }
    }

    fun clearCache(url: String) {
        streamManager.clearCache(url)
    }
}
