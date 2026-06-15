package ua.ukrtv.app.data.network

import android.util.Log
import kotlinx.coroutines.delay
import ua.ukrtv.app.Constants

class RetryPolicy(
    private val maxRetries: Int = Constants.MAX_RETRIES,
    private val retryDelayMs: Long = Constants.RETRY_DELAY_MS,
) {

    suspend fun <T> runWithRetries(
        tag: String,
        block: suspend (attempt: Int) -> T,
        onRetryDelay: suspend (delayTimeMs: Long, attempt: Int) -> Unit = { d, _ -> delay(d) }
    ): T? {
        var lastError: Exception? = null

        repeat(maxRetries) { attempt ->
            // keep retry loop scoped; all returns below must be inside this loop

            try {
                return block(attempt)
            } catch (e: Exception) {
                lastError = e

                if (attempt >= maxRetries - 1) return null

                // Permanent errors
                if (e.message?.contains("404") == true) {
                    Log.w(tag, "HTTP 404 is permanent, skipping retries")
                    return null
                }

                val delayTime = when {
                    e.message?.contains("429") == true -> 3000L * (attempt + 1)
                    e.message?.contains("403") == true || e.message?.contains("503") == true -> 500L
                    else -> retryDelayMs
                }

                Log.w(tag, "Retry ${attempt + 1}/$maxRetries (Error: ${e.message}) in ${delayTime}ms")
                onRetryDelay(delayTime, attempt)
            }
        }

        Log.e(tag, "Error after $maxRetries attempts: ${lastError?.message}")
        return null
    }
}

