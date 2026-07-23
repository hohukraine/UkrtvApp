package ua.ukrtv.app.player

import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.util.AppLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderSpeedTester @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val PROBE_BYTES = 524288L
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val READ_TIMEOUT_MS = 8000L
    }

    data class SpeedTestResult(
        val providerName: String,
        val url: String,
        val timeToFirstByteMs: Long,
        val throughputKbps: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun testSpeed(
        providerName: String,
        url: String,
        referer: String = ""
    ): SpeedTestResult? {
        val start = System.currentTimeMillis()
        return try {
            val client = okHttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .apply { if (referer.isNotBlank()) header("Referer", referer) }
                .header("Range", "bytes=0-$PROBE_BYTES")
                .build()

            val response = client.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - start
            val body = response.body ?: return null
            val bytesRead = body.byteStream().use { stream ->
                val buf = ByteArray(8192)
                var total = 0L
                while (total < PROBE_BYTES) {
                    val read = stream.read(buf, 0, buf.size.coerceAtMost((PROBE_BYTES - total).toInt()))
                    if (read == -1) break
                    total += read
                }
                total
            }
            val throughputKbps = if (elapsed > 0 && bytesRead > 0) {
                (bytesRead.toDouble() / 1024.0) / (elapsed.toDouble() / 1000.0)
            } else 0.0

            response.close()

            val result = SpeedTestResult(
                providerName = providerName,
                url = url,
                timeToFirstByteMs = elapsed,
                throughputKbps = throughputKbps
            )
            AppLogger.d("ProviderSpeedTester", "$providerName: ${url.take(50)}... TTFB=${elapsed}ms throughput=${"%.1f".format(throughputKbps)}KB/s")
            result
        } catch (e: Exception) {
            AppLogger.d("ProviderSpeedTester", "Speed test failed for $providerName: ${e.message}")
            null
        }
    }

    fun selectBest(
        primary: SpeedTestResult?,
        alternate: SpeedTestResult?,
        minImprovementFactor: Double = 1.3
    ): SpeedTestResult? {
        if (primary == null && alternate == null) return null
        if (primary == null) return alternate
        if (alternate == null) return primary
        if (alternate.throughputKbps > primary.throughputKbps * minImprovementFactor) return alternate
        if (alternate.timeToFirstByteMs > 0 && primary.timeToFirstByteMs > 0 &&
            primary.timeToFirstByteMs.toDouble() / alternate.timeToFirstByteMs.toDouble() > minImprovementFactor &&
            alternate.throughputKbps >= primary.throughputKbps * 0.8
        ) return alternate
        return primary
    }
}
