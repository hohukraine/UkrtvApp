package ua.ukrtv.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.model.CachedMovie
import javax.inject.Inject
import javax.inject.Singleton

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

@Singleton
class FavoritesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.favoritesDataStore
    private val gson = Gson()
    private val FAVORITES_KEY = stringPreferencesKey("favorite_movies")

    suspend fun getFavorites(): List<CachedMovie> = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val json = prefs[FAVORITES_KEY] ?: return@withContext emptyList()
        val type = object : TypeToken<List<CachedMovie>>() {}.type
        try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun isFavorite(id: String): Boolean = withContext(Dispatchers.IO) {
        getFavorites().any { it.id == id }
    }

    suspend fun toggleFavorite(movie: CachedMovie) = withContext(Dispatchers.IO) {
        val current = getFavorites().toMutableList()
        val existing = current.find { it.id == movie.id }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(0, movie)
        }
        dataStore.edit { preferences ->
            preferences[FAVORITES_KEY] = gson.toJson(current)
        }
    }
}
