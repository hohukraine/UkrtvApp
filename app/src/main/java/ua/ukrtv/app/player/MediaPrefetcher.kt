package ua.ukrtv.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.ukrtv.app.util.AppLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MediaPrefetcher @Inject constructor() {

    companion object {
        private const val CACHE_DIR = "media_cache"
        private const val MAX_CACHE_BYTES = 150L * 1024 * 1024
        private const val PREFETCH_BYTES_LOW = 2L * 1024 * 1024
        private const val PREFETCH_BYTES_MID = 3L * 1024 * 1024
        private const val PREFETCH_BYTES_HIGH = 5L * 1024 * 1024
    }

    private var cache: SimpleCache? = null
    private var activePrefetch: Job? = null

    fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.filesDir, CACHE_DIR),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context)
            ).also { cache = it }
        }
    }

    fun getCachedDataSourceFactory(context: Context, upstreamFactory: DataSource.Factory): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun prefetch(
        context: Context,
        url: String,
        headers: Map<String, String>,
        upstreamFactory: DataSource.Factory,
        scope: kotlinx.coroutines.CoroutineScope,
        prefetchBytes: Long = PREFETCH_BYTES_MID
    ) {
        activePrefetch?.cancel()
        activePrefetch = scope.launch(Dispatchers.IO) {
            try {
                val cacheDsFactory = getCachedDataSourceFactory(context, upstreamFactory)
                val ds = cacheDsFactory.createDataSource()
                val dataSpec = DataSpec.Builder()
                    .setUri(url)
                    .setHttpRequestHeaders(headers)
                    .setLength(prefetchBytes)
                    .build()
                var bytesRead = 0L
                ds.open(dataSpec)
                val buf = ByteArray(64 * 1024)
                try {
                    while (isActive && bytesRead < prefetchBytes) {
                        val read = ds.read(buf, 0, buf.size)
                        if (read == -1) break
                        bytesRead += read
                    }
                } finally {
                    ds.close()
                }
                if (bytesRead > 0) {
                    AppLogger.d("MediaPrefetcher", "Prefetched ${bytesRead / 1024}KB for ${url.take(60)}")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    AppLogger.d("MediaPrefetcher", "Prefetch failed: ${e.message}")
                }
            }
        }
    }

    fun cancelPrefetch() {
        activePrefetch?.cancel()
        activePrefetch = null
    }

    fun release() {
        cancelPrefetch()
        cache?.release()
        cache = null
    }
}
