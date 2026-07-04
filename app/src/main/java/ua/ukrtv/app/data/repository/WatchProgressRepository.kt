package ua.ukrtv.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ua.ukrtv.app.domain.model.WatchProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val PROGRESS_PREFIX = "p_"

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
                try { json.decodeFromString<WatchProgress>(it) } catch (_: Exception) { null }
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
            preferences[prefKey] = json.encodeToString(updated)
        }
    }

    suspend fun getProgress(contentId: String, episodeId: String? = null): WatchProgress? {
        val keyString = if (episodeId != null) "${contentId}_$episodeId" else contentId
        val prefKey = stringPreferencesKey("$PROGRESS_PREFIX$keyString")

        val stored = dataStore.data.map { it[prefKey] }.first()
        return stored?.let {
            try { json.decodeFromString<WatchProgress>(it) } catch (_: Exception) { null }
        }
    }

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

    fun getAllProgress(): Flow<List<WatchProgress>> {
        return dataStore.data.map { preferences ->
            preferences.asMap()
                .filterKeys { it.name.startsWith(PROGRESS_PREFIX) }
                .values
                .mapNotNull { it as? String }
                .mapNotNull {
                    try { json.decodeFromString<WatchProgress>(it) } catch (_: Exception) { null }
                }
                .sortedByDescending { it.timestamp }
        }
    }
}
