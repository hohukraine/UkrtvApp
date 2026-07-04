package ua.ukrtv.app.data.streaming

import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

data class ResolutionContext(
    val season: Int? = null,
    val episode: Int? = null,
    val voiceover: String? = null,
    val isDeep: Boolean = false,
    val referer: String = "",
    val prefetchedHtml: String? = null
)

interface ResolutionStrategy {
    val name: String
    suspend fun canHandle(url: String, context: ResolutionContext): Boolean
    suspend fun resolve(url: String, context: ResolutionContext): StreamResolutionResult?
}

class ResolutionChain(
    private val strategies: List<ResolutionStrategy>,
    private val logger: ua.ukrtv.app.util.ResolutionLogger
) {
    suspend fun resolve(url: String, context: ResolutionContext): StreamResolutionResult? {
        val sectionName = "ResolutionChain.${context.isDeep}"
        PerformanceMonitor.begin(sectionName)
        logger.log(url, "Chain", "Starting resolution (deep=${context.isDeep})")
        try {
            for (strategy in strategies) {
                try {
                    if (strategy.canHandle(url, context)) {
                        logger.log(url, strategy.name, "Attempting resolution")
                        val result = strategy.resolve(url, context)
                        if (result != null) {
                            logger.log(url, strategy.name, "Success: ${result.streamUrl.take(30)}...")
                            return result
                        } else {
                            logger.log(url, strategy.name, "Returned null", isError = true)
                        }
                    }
                } catch (e: Exception) {
                    logger.log(url, strategy.name, "Error: ${e.message}", isError = true)
                }
            }
            logger.log(url, "Chain", "Resolution failed for all strategies", isError = true)
            return null
        } finally {
            PerformanceMonitor.end()
        }
    }
}
