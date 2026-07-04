package ua.ukrtv.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ua.ukrtv.app.domain.model.HomeSection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeCacheRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    private fun getCacheKey(providerName: String) = stringPreferencesKey("home_cache_v2_$providerName")
    private fun getTimeKey(providerName: String) = longPreferencesKey("home_cache_time_v2_$providerName")

    suspend fun getHomeCache(providerName: String): List<HomeSection>? {
        val prefs = dataStore.data.firstOrNull() ?: return null
        val cached = prefs[getCacheKey(providerName)] ?: return null
        return try {
            json.decodeFromString<List<HomeSection>>(cached)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCacheTimestamp(providerName: String): Long {
        return dataStore.data.firstOrNull()?.get(getTimeKey(providerName)) ?: 0L
    }

    suspend fun saveHomeCache(providerName: String, sections: List<HomeSection>) {
        dataStore.edit { prefs ->
            prefs[getCacheKey(providerName)] = json.encodeToString(sections)
            prefs[getTimeKey(providerName)] = System.currentTimeMillis()
        }
    }

    suspend fun saveEmptyCache(providerName: String) {
        dataStore.edit { prefs ->
            // Save empty cache with zero timestamp — always expired on next open
            prefs[getCacheKey(providerName)] = "[]"
            prefs[getTimeKey(providerName)] = 0L
        }
    }
}
