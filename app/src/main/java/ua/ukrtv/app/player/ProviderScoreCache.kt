package ua.ukrtv.app.player

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderScoreCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SCORE_TTL_MS = 24L * 60 * 60 * 1000
        private const val PREFS_NAME = "provider_speed_cache"
    }

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

    fun record(providerName: String, result: ProviderSpeedTester.SpeedTestResult) {
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

    fun markSlow(providerName: String) {
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

    fun clear() {
        scores.clear()
        prefs.edit().clear().apply()
    }

    fun clearProvider(providerName: String) {
        scores.remove(providerName)
        removeFromPrefs(providerName)
    }
}
