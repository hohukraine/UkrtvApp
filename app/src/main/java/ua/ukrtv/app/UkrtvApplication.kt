package ua.ukrtv.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.StrictMode
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toPath
import coil3.request.CachePolicy
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.player.MediaPrefetcher

import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.util.hasMediatekChipset
import ua.ukrtv.app.util.CrashReporter
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class UkrtvApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    companion object {
        val appStartTime = System.nanoTime()
    }

    @Inject
    lateinit var okHttpClient: Lazy<OkHttpClient>

    @Inject
    lateinit var providerManager: Lazy<ProviderManager>

    @Inject
    lateinit var contentRepository: Lazy<ua.ukrtv.app.data.repository.ContentRepository>

    @Inject
    lateinit var htmlHttpClient: Lazy<ua.ukrtv.app.data.network.HtmlHttpClient>

    @Inject
    lateinit var workerFactory: dagger.Lazy<androidx.hilt.work.HiltWorkerFactory>

    @Inject
    lateinit var watchProgressRepository: Lazy<ua.ukrtv.app.data.repository.WatchProgressRepository>

    @Inject
    lateinit var mediaPrefetcher: MediaPrefetcher

    private var imageLoader: ImageLoader? = null
    private val sharedImageDispatcher by lazy {
        Dispatchers.IO.limitedParallelism(4)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.d("UkrtvApplication", "onCreate")

        val hardware = getDeviceClass(this)
        val isMediatek = hasMediatekChipset()

        SingletonImageLoader.setSafe(object : SingletonImageLoader.Factory {
            override fun newImageLoader(context: Context): ImageLoader {
                return buildImageLoader(this@UkrtvApplication, hardware, isMediatek, reuseCurrent = true)
            }
        })

        if (BuildConfig.DEBUG) {
            AppLogger.d("Startup", "Hilt init: ${(System.nanoTime() - appStartTime) / 1_000_000}ms")
        }
        val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        CrashReporter.init(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        AppLogger.d("ProcessLifecycle", "App moved to foreground (since class load: ${(System.nanoTime() - appStartTime) / 1_000_000}ms)")
                        htmlHttpClient.get().restart()
                        prewarmScope.launch {
                            try { watchProgressRepository.get().cleanupOldEntries() } catch (_: Exception) {}
                        }
                        prewarmScope.launch {
                            delay(2000)
                            try {
                                val provider = providerManager.get().activeProvider.value
                                contentRepository.get().getHomeGrid(provider).firstOrNull()
                                AppLogger.d("Prewarm", "HomeCache prewarm completed for ${provider.name}")
                            } catch (_: Exception) { }
                        }
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        AppLogger.d("ProcessLifecycle", "App moved to background")
                        prewarmScope.launch {
                            htmlHttpClient.get().shutdown()
                        }
                    }
                    else -> {}
                }
            }
        )

        prewarmScope.launch {
            if (BuildConfig.DEBUG) {
                delay(10000) // Even longer delay for StrictMode to avoid false positives during heavy init
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
            
            // Significant delay for catalog update to reduce startup pressure
            delay(30000) 
            scheduleCatalogUpdate()
            
            delay(10000)
            try {
                val repo = contentRepository.get()
                val provider = providerManager.get().activeProvider.value
                if (repo.isHomeCacheStale(provider.name)) {
                    AppLogger.d("Prewarm", "Home cache stale, refreshing for ${provider.name}")
                    repo.getTrendsForGrid()
                }
            } catch (_: Exception) { }
        }
    }

    private fun scheduleCatalogUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val catalogWork = PeriodicWorkRequestBuilder<ua.ukrtv.app.worker.CatalogUpdateWorker>(
            12, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "catalog_update",
            ExistingPeriodicWorkPolicy.KEEP,
            catalogWork
        )
        AppLogger.i("UkrtvApplication", "Catalog update scheduled every 12 hours")
    }

    private fun clearCaches() {
        imageLoader?.memoryCache?.clear()
        providerManager.get().clearCaches()
        try { htmlHttpClient.get().clearMemoryCache() } catch(_: Exception) {}
        try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        AppLogger.d("UkrtvApplication", "newImageLoader requested")
        val hardware = getDeviceClass(this)
        val loader = buildImageLoader(this, hardware, hasMediatekChipset(), reuseCurrent = false)
        imageLoader = loader
        return loader
    }

    fun applyImageLoaderFor(deviceClass: DeviceClass, isMediatek: Boolean) {
        AppLogger.d("UkrtvApplication", "applyImageLoaderFor: $deviceClass")
        SingletonImageLoader.setSafe(object : SingletonImageLoader.Factory {
            override fun newImageLoader(context: Context): ImageLoader {
                return buildImageLoader(this@UkrtvApplication, deviceClass, isMediatek, reuseCurrent = true)
            }
        })
    }

    private fun buildImageLoader(
        context: Context,
        deviceClass: DeviceClass,
        isMediatek: Boolean,
        reuseCurrent: Boolean
    ): ImageLoader {
        val memClass = (context.getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
        val maxHeapBytes = memClass * 1024L * 1024L
        val memPct = when (deviceClass) {
            DeviceClass.LOW -> 0.08
            DeviceClass.MID -> 0.12
            DeviceClass.HIGH -> 0.20
        }
        val adaptiveSize = (maxHeapBytes * memPct).toInt()
        val diskCacheSize = when (deviceClass) {
            DeviceClass.LOW -> 32L * 1024 * 1024
            DeviceClass.MID -> 64L * 1024 * 1024
            DeviceClass.HIGH -> 200L * 1024 * 1024
        }

        return ImageLoader.Builder(context)
            .components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() }))
            }
            .eventListener(object : coil3.EventListener() {
                override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                    AppLogger.e("Coil", "Error loading ${request.data}: ${result.throwable.message}")
                }
            })
            .coroutineContext(sharedImageDispatcher)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes { adaptiveSize.toLong() }
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            .allowRgb565(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .allowHardware(deviceClass != DeviceClass.LOW && !isMediatek)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(100)
            .build()
            .also { if (reuseCurrent) {
                AppLogger.d("UkrtvApplication", "ImageLoader initialized for class $deviceClass")
                imageLoader = it
            } }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            imageLoader?.memoryCache?.clear()
            providerManager.get().clearCaches()
            try { htmlHttpClient.get().clearMemoryCache() } catch(_: Exception) {}
            try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
            try { mediaPrefetcher.cancelPrefetch() } catch(_: Exception) {}
        }
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            clearCaches()
        }
    }
}
