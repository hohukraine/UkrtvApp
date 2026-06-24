package ua.ukrtv.app.data.api

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import okhttp3.CookieJar

/**
 * Pure HTTP Cloudflare Resolver. 
 * WebView usage has been removed to optimize performance on Android TV.
 */
@Singleton
class CloudflareResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cfState: CloudflareState,
    private val cookieJar: CookieJar
) {
    private val hostLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /**
     * In the pure HTTP version, we don't resolve JS challenges.
     * We only manage state and blocking periods to prevent request floods.
     */
    suspend fun resolve(url: String): Boolean {
        val host = try { java.net.URI(url).host } catch (e: Exception) { null } ?: return false
        val hostMutex = hostLocks.getOrPut(host) { Mutex() }

        return hostMutex.withLock {
            if (!cfState.isBlocked(host)) return@withLock true
            
            Log.w("CloudflareResolver", "Domain $host is blocked (Cloudflare Challenge detected). Pure HTTP resolution is not possible.")
            // Since we removed WebView, we can't solve JS challenges.
            // We just return false to let the caller handle the failure.
            false
        }
    }
}
