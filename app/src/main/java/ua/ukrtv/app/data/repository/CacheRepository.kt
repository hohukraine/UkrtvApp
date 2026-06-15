package ua.ukrtv.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.model.CachedMovie
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val gson = Gson()
    private val MOVIES_KEY = stringPreferencesKey("available_movies")
    private val TIME_KEY = longPreferencesKey("last_check_time")

    suspend fun getAvailableMovies(): List<CachedMovie> = withContext(Dispatchers.IO) {
        try {
            val prefs = dataStore.data.first()
            val json = prefs[MOVIES_KEY] ?: return@withContext emptyList()
            val type = object : TypeToken<List<CachedMovie>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMovieById(id: String): CachedMovie? {
        return getAvailableMovies().find { it.id == id }
    }

    suspend fun saveAvailableMovies(movies: List<CachedMovie>) = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs[MOVIES_KEY] = gson.toJson(movies)
        }
    }

    suspend fun getLastCheckTime(): Long {
        return dataStore.data.first()[TIME_KEY] ?: 0L
    }

    suspend fun saveLastCheckTime(time: Long) {
        dataStore.edit { prefs ->
            prefs[TIME_KEY] = time
        }
    }

    suspend fun clearCache() {
        dataStore.edit { prefs ->
            prefs.remove(MOVIES_KEY)
            prefs.remove(TIME_KEY)
        }
    }
}
