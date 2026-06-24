package ua.ukrtv.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val TTL_MS = 12 * 60 * 60 * 1000L // 12 hours

    suspend fun getSessionHash(providerName: String): String? {
        val hashKey = stringPreferencesKey("session_hash_$providerName")
        val timestampKey = longPreferencesKey("session_time_$providerName")
        
        val prefs = dataStore.data.first()
        val hash = prefs[hashKey] ?: return null
        val timestamp = prefs[timestampKey] ?: 0L
        
        if (System.currentTimeMillis() - timestamp > TTL_MS) {
            return null
        }
        return hash
    }

    suspend fun saveSessionHash(providerName: String, hash: String) {
        val hashKey = stringPreferencesKey("session_hash_$providerName")
        val timestampKey = longPreferencesKey("session_time_$providerName")
        
        dataStore.edit { prefs ->
            prefs[hashKey] = hash
            prefs[timestampKey] = System.currentTimeMillis()
        }
    }
}
