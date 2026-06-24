package ua.ukrtv.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.ukrtv.app.domain.model.PlaybackStats
import ua.ukrtv.app.domain.model.WatchProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    private val PROGRESS_PREFIX = "p_"
    private val STATS_PREFIX = "s_"

    fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    suspend fun saveProgress(
        contentId: String,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long,
        title: String = "",
        poster: String = "",
        pageUrl: String = ""
    ) {
        val keyString = if (episodeId != null) "${contentId}_$episodeId" else contentId
        val prefKey = stringPreferencesKey("$PROGRESS_PREFIX$keyString")
        
        dataStore.edit { preferences ->
            val existingJson = preferences[prefKey]
            val existing = existingJson?.let { 
                try { gson.fromJson(it, WatchProgress::class.java) } catch (_: Exception) { null }
            }
            
            val updated = WatchProgress(
                contentId = contentId,
                episodeId = episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
                title = title.ifEmpty { existing?.title ?: "" },
                poster = poster.ifEmpty { existing?.poster ?: "" },
                pageUrl = pageUrl.ifEmpty { existing?.pageUrl ?: "" },
                timestamp = System.currentTimeMillis()
            )
            preferences[prefKey] = gson.toJson(updated)
        }
    }

    suspend fun getProgress(contentId: String, episodeId: String? = null): WatchProgress? {
        val keyString = if (episodeId != null) "${contentId}_$episodeId" else contentId
        val prefKey = stringPreferencesKey("$PROGRESS_PREFIX$keyString")
        
        val json = dataStore.data.map { it[prefKey] }.first()
        return json?.let { 
            try { gson.fromJson(it, WatchProgress::class.java) } catch (_: Exception) { null }
        }
    }

    /**
     * Placeholder for cloud sync. Currently returns null as remote progress is not implemented.
     */
    suspend fun getProgressWithDeviceInfo(contentId: String, episodeId: String? = null): WatchProgress? {
        return null 
    }

    suspend fun deleteProgress(contentId: String) {
        val prefix = "$PROGRESS_PREFIX$contentId"
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }

    suspend fun getAllProgress(): List<WatchProgress> {
        return dataStore.data.map { preferences ->
            preferences.asMap()
                .filterKeys { it.name.startsWith(PROGRESS_PREFIX) }
                .values
                .mapNotNull { it as? String }
                .mapNotNull { 
                    try { gson.fromJson(it, WatchProgress::class.java) } catch (_: Exception) { null }
                }
                .sortedByDescending { it.timestamp }
        }.first()
    }

    suspend fun savePlaybackStats(stats: PlaybackStats) {
        val key = "${stats.contentId}_${stats.episodeId ?: "movie"}_${stats.timestamp}"
        val prefKey = stringPreferencesKey("$STATS_PREFIX$key")
        
        dataStore.edit { preferences ->
            preferences[prefKey] = gson.toJson(stats)
        }
    }

    suspend fun getAllStats(): List<PlaybackStats> {
        return dataStore.data.map { preferences ->
            preferences.asMap()
                .filterKeys { it.name.startsWith(STATS_PREFIX) }
                .values
                .mapNotNull { it as? String }
                .mapNotNull { 
                    try { gson.fromJson(it, PlaybackStats::class.java) } catch (_: Exception) { null }
                }
                .sortedByDescending { it.timestamp }
        }.first()
    }
}
