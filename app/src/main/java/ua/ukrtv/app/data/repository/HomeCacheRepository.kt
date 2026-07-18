package ua.ukrtv.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.util.AppLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private fun getTimeKey(providerName: String) = longPreferencesKey("home_cache_time_v3_$providerName")
    private fun getCacheFile(providerName: String) = File(context.cacheDir, "home_cache_$providerName.json")

    suspend fun getHomeCache(providerName: String): List<HomeSection>? = withContext(Dispatchers.IO) {
        val file = getCacheFile(providerName)
        if (!file.exists()) return@withContext null
        try {
            val cached = file.readText()
            json.decodeFromString<List<HomeSection>>(cached)
        } catch (e: Exception) {
            AppLogger.e("HomeCache", "Failed to read cache file for $providerName", e)
            null
        }
    }

    suspend fun getCacheTimestamp(providerName: String): Long {
        return dataStore.data.firstOrNull()?.get(getTimeKey(providerName)) ?: 0L
    }

    suspend fun saveHomeCache(providerName: String, sections: List<HomeSection>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(sections)
            getCacheFile(providerName).writeText(content)
            dataStore.edit { prefs ->
                prefs[getTimeKey(providerName)] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            AppLogger.e("HomeCache", "Failed to save cache file for $providerName", e)
        }
    }

    suspend fun saveEmptyCache(providerName: String) = withContext(Dispatchers.IO) {
        try {
            getCacheFile(providerName).delete()
            dataStore.edit { prefs ->
                prefs[getTimeKey(providerName)] = 0L
            }
        } catch (_: Exception) {}
    }
}
