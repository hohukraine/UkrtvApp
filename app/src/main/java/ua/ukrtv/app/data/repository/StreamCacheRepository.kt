package ua.ukrtv.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.providers.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

private val Context.streamCacheDataStore by preferencesDataStore(name = "stream_cache")

@Singleton
class StreamCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.streamCacheDataStore
    private val gson = Gson()
    private val cacheDuration = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun getCachedStream(url: String): MediaSource? = withContext(Dispatchers.IO) {
        val key = stringPreferencesKey(url.hashCode().toString())
        val typeKey = stringPreferencesKey("${url.hashCode()}_type")
        val timeKey = longPreferencesKey("${url.hashCode()}_time")
        
        val prefs = dataStore.data.first()
        val timestamp = prefs[timeKey] ?: 0L
        
        if (System.currentTimeMillis() - timestamp < cacheDuration) {
            val json = prefs[key] ?: return@withContext null
            val typeName = prefs[typeKey]
            
            return@withContext try {
                if (typeName != null) {
                    val clazz = Class.forName(typeName)
                    gson.fromJson(json, clazz) as? MediaSource
                } else {
                    // Fallback for older cache
                    if (json.contains("seasons")) {
                        gson.fromJson(json, MediaSource.Series::class.java)
                    } else {
                        gson.fromJson(json, MediaSource.Movie::class.java)
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
        null
    }

    suspend fun cacheStream(url: String, source: MediaSource) = withContext(Dispatchers.IO) {
        val key = stringPreferencesKey(url.hashCode().toString())
        val typeKey = stringPreferencesKey("${url.hashCode()}_type")
        val timeKey = longPreferencesKey("${url.hashCode()}_time")
        
        dataStore.edit { preferences ->
            preferences[key] = gson.toJson(source)
            preferences[typeKey] = source.javaClass.name
            preferences[timeKey] = System.currentTimeMillis()
        }
    }
}
