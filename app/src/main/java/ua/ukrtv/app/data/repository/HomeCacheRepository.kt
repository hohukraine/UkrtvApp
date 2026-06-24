package ua.ukrtv.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import ua.ukrtv.app.domain.model.HomeSection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeCacheRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    
    private fun getCacheKey(providerName: String) = stringPreferencesKey("home_cache_$providerName")
    private fun getTimeKey(providerName: String) = longPreferencesKey("home_cache_time_$providerName")

    suspend fun getHomeCache(providerName: String): List<HomeSection>? {
        val prefs = dataStore.data.firstOrNull() ?: return null
        val json = prefs[getCacheKey(providerName)] ?: return null
        val type = object : TypeToken<List<HomeSection>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCacheTimestamp(providerName: String): Long {
        return dataStore.data.firstOrNull()?.get(getTimeKey(providerName)) ?: 0L
    }

    suspend fun saveHomeCache(providerName: String, sections: List<HomeSection>) {
        dataStore.edit { prefs ->
            prefs[getCacheKey(providerName)] = gson.toJson(sections)
            prefs[getTimeKey(providerName)] = System.currentTimeMillis()
        }
    }

    suspend fun clearCache(providerName: String) {
        dataStore.edit { prefs ->
            prefs.remove(getCacheKey(providerName))
            prefs.remove(getTimeKey(providerName))
        }
    }
}
