package ua.ukrtv.app.player

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.util.AppLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderQualityManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val REBUFFER_WINDOW_MS = 60_000L
        private const val MAX_REBUFFERS_IN_WINDOW = 4
        private const val UNHEALTHY_THROUGHPUT_KBPS = 500.0
        private const val PROBE_BYTES = 524288L
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val READ_TIMEOUT_MS = 8000L
        private const val SCORE_TTL_MS = 24L * 60 * 60 * 1000
        private const val PREFS_NAME = "provider_speed_cache"
    }

    // ── Stream Health ────────────────────────────────────────────────────

    data class HealthState(
        val isHealthy: Boolean = true,
        val rebufferCount: Int = 0,
        val currentThroughputKbps: Double = 0.0,
        val reason: String = ""
    )

    private val rebufferEvents = mutableListOf<Long>()
    private var lastPosition: Long = 0L
    private var lastPositionTime: Long = 0L
    private var stalledDuration: Long = 0L

    private var _healthState = HealthState()
    val healthState: HealthState get() = _healthState

    fun onRebuffer() {
        val now = System.currentTimeMillis()
        rebufferEvents.add(now)
        trimRebufferEvents(now)
        val count = rebufferEvents.size

        AppLogger.d("ProviderQuality", "Rebuffer event #$count in last ${REBUFFER_WINDOW_MS / 1000}s")

        if (count > MAX_REBUFFERS_IN_WINDOW) {
            _healthState = HealthState(
                isHealthy = false,
                rebufferCount = count,
                reason = "Too many rebuffers ($count in 60s)"
            )
        }
    }

    fun onPositionUpdate(positionMs: Long) {
        val now = System.currentTimeMillis()
        if (lastPosition > 0 && lastPositionTime > 0) {
            val dt = now - lastPositionTime
            val dp = positionMs - lastPosition
            if (dt > 0 && dp >= 0) {
                val speedFactor = dp.toDouble() / dt.toDouble()
                if (speedFactor < 0.5 && dt > 5000) {
                    stalledDuration += dt
                    if (stalledDuration > 10_000 && _healthState.isHealthy) {
                        _healthState = HealthState(
                            isHealthy = false,
                            rebufferCount = rebufferEvents.size,
                            reason = "Playback stalled for ${stalledDuration / 1000}s"
                        )
                    }
                } else {
                    stalledDuration = 0
                }
            }
        }
        lastPosition = positionMs
        lastPositionTime = now
    }

    fun onThroughputUpdate(throughputKbps: Double) {
        _healthState = _healthState.copy(currentThroughputKbps = throughputKbps)
        if (throughputKbps > 0 && throughputKbps < UNHEALTHY_THROUGHPUT_KBPS && _healthState.isHealthy) {
            _healthState = HealthState(
                isHealthy = false,
                rebufferCount = rebufferEvents.size,
                currentThroughputKbps = throughputKbps,
                reason = "Low throughput: ${"%.0f".format(throughputKbps)}KB/s"
            )
        }
    }

    fun markHealthy() {
        _healthState = HealthState()
        rebufferEvents.clear()
        stalledDuration = 0
    }

    fun markHealthyForFallback() {
        _healthState = HealthState(isHealthy = true, rebufferCount = 0)
        stalledDuration = 0
    }

    fun resetHealth() {
        _healthState = HealthState()
        rebufferEvents.clear()
        lastPosition = 0L
        lastPositionTime = 0L
        stalledDuration = 0
    }

    private fun trimRebufferEvents(now: Long) {
        val cutoff = now - REBUFFER_WINDOW_MS
        rebufferEvents.removeAll { it < cutoff }
    }

    // ── Speed Testing ────────────────────────────────────────────────────

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
            AppLogger.d("ProviderQuality", "Speed test $providerName: ${url.take(50)}... TTFB=${elapsed}ms throughput=${"%.1f".format(throughputKbps)}KB/s")
            result
        } catch (e: Exception) {
            AppLogger.d("ProviderQuality", "Speed test failed for $providerName: ${e.message}")
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

    // ── Score Cache ──────────────────────────────────────────────────────

    @Serializable
    data class ProviderScore(
        val providerName: String,
        val url: String,
        val throughputKbps: Double,
        val timeToFirstByteMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() - timestamp < SCORE_TTL_MS && throughputKbps > 0
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scores = loadAllFromPrefs()

    private fun loadAllFromPrefs(): MutableMap<String, ProviderScore> {
        val map = mutableMapOf<String, ProviderScore>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("score_") && value is String) {
                try {
                    val score = json.decodeFromString<ProviderScore>(value)
                    if (score.isValid()) {
                        map[score.providerName] = score
                    }
                } catch (_: Exception) { }
            }
        }
        return map
    }

    private fun saveToPrefs(score: ProviderScore) {
        prefs.edit().putString("score_${score.providerName}", json.encodeToString(score)).apply()
    }

    private fun removeFromPrefs(providerName: String) {
        prefs.edit().remove("score_$providerName").apply()
    }

    fun recordScore(providerName: String, result: SpeedTestResult) {
        val score = ProviderScore(
            providerName = providerName,
            url = result.url,
            throughputKbps = result.throughputKbps,
            timeToFirstByteMs = result.timeToFirstByteMs,
            timestamp = result.timestamp
        )
        scores[providerName] = score
        saveToPrefs(score)
    }

    fun getBestProvider(exclude: String? = null): ProviderScore? {
        return scores
            .filter { (name, score) ->
                val valid = score.isValid()
                val notExcluded = exclude == null || name != exclude
                valid && notExcluded
            }
            .maxByOrNull { (_, score) -> score.throughputKbps }
            ?.value
    }

    fun getScore(providerName: String): ProviderScore? {
        val score = scores[providerName]
        return if (score != null && score.isValid()) score else null
    }

    fun markProviderSlow(providerName: String) {
        val current = scores[providerName]
        if (current != null) {
            val updated = current.copy(
                throughputKbps = current.throughputKbps * 0.5,
                timestamp = System.currentTimeMillis()
            )
            scores[providerName] = updated
            saveToPrefs(updated)
        }
    }

    fun clearScores() {
        scores.clear()
        prefs.edit().clear().apply()
    }

    fun clearProviderScore(providerName: String) {
        scores.remove(providerName)
        removeFromPrefs(providerName)
    }

    fun toSpeedTestResult(score: ProviderScore) = SpeedTestResult(
        providerName = score.providerName,
        url = score.url,
        timeToFirstByteMs = score.timeToFirstByteMs,
        throughputKbps = score.throughputKbps,
        timestamp = score.timestamp
    )
}
