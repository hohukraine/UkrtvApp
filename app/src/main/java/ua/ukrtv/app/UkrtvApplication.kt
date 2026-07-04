package ua.ukrtv.app

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.CrashReporter
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject

@HiltAndroidApp
class UkrtvApplication : Application(), ImageLoaderFactory {
    companion object {
        val appStartTime = System.nanoTime()
    }

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var providerManager: ProviderManager

    @Inject
    lateinit var contentRepository: ua.ukrtv.app.data.repository.ContentRepository

    @Inject
    lateinit var htmlHttpClient: ua.ukrtv.app.data.network.HtmlHttpClient

    @Inject
    lateinit var networkMonitor: ua.ukrtv.app.util.NetworkMonitor

    private var imageLoader: ImageLoader? = null
    private var initJob: kotlinx.coroutines.Job? = null

    private var sessionInitialized = false
    private val initLock = Any()

    fun deferredInitIfNeeded() {
        if (sessionInitialized) return
        synchronized(initLock) {
            if (sessionInitialized) return
            initJob?.cancel()
            initJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    providerManager.activeProvider.value.initializeSession()
                    AppLogger.d("AppInit", "Session initialized for ${providerManager.activeProvider.value.name}")
                    sessionInitialized = true
                } catch (e: Exception) {
                    AppLogger.w("AppInit", "Session init failed: ${e.message}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        CrashReporter.init(this)
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
        deferredInitIfNeeded()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        AppLogger.d("ProcessLifecycle", "App moved to foreground")
                        deferredInitIfNeeded()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        AppLogger.d("ProcessLifecycle", "App moved to background")
                    }
                    else -> {}
                }
            }
        )
    }

    private fun clearCaches() {
        imageLoader?.memoryCache?.clear()
        providerManager.clearCaches()
    }

    override fun newImageLoader(): ImageLoader {
        val memClass = (getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
        val maxHeapBytes = memClass * 1024L * 1024L
        val adaptiveSize = when {
            memClass <= 128 -> (maxHeapBytes * 0.08).toInt()
            memClass <= 256 -> (maxHeapBytes * 0.12).toInt()
            else -> (maxHeapBytes * 0.15).toInt()
        }
        val diskCacheSize = when {
            memClass <= 96 -> 32L * 1024 * 1024
            memClass <= 192 -> 64L * 1024 * 1024
            memClass <= 384 -> 128L * 1024 * 1024
            else -> 192L * 1024 * 1024
        }

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(adaptiveSize)
                    .strongReferencesEnabled(memClass > 128)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .respectCacheHeaders(true)
            .build()
            .also { imageLoader = it }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            imageLoader?.memoryCache?.clear()
            providerManager.clearCaches()
        }
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            clearCaches()
        }
    }
}
