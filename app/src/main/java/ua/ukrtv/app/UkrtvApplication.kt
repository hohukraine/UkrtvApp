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
import coil3.svg.SvgDecoder
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
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.util.hasMediatekChipset
import ua.ukrtv.app.util.CrashReporter
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.profileinstaller.ProfileInstaller
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
    lateinit var top200Repository: Lazy<ua.ukrtv.app.data.repository.Top200Repository>

    @Volatile
    private var imageLoader: ImageLoader? = null
    private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cachedMemoryClass: Int = 0

    private val sharedImageDispatcher by lazy {
        val deviceClass = getDeviceClass(this)
        val parallelism = when (deviceClass) {
            DeviceClass.LOW -> 2
            else -> 4
        }
        Dispatchers.IO.limitedParallelism(parallelism)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()

    override fun onCreate() {
        super.onCreate()
        cachedMemoryClass = (getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
        AppLogger.init(this)
        AppLogger.d("UkrtvApplication", "onCreate")

        prewarmScope.launch(Dispatchers.IO) {
            ProfileInstaller.writeProfile(this@UkrtvApplication)
        }

        if (BuildConfig.DEBUG) {
            AppLogger.d("Startup", "Hilt init: ${(System.nanoTime() - appStartTime) / 1_000_000}ms")
        }
        
        prewarmScope.launch {
            imageLoader = buildImageLoader(this@UkrtvApplication, getDeviceClass(this@UkrtvApplication), hasMediatekChipset(), reuseCurrent = true)
        }

        prewarmScope.launch {
            CrashReporter.init(this@UkrtvApplication)
        }

        // Phase 1: Pre-warm Home data immediately
        prewarmScope.launch {
            try {
                val provider = providerManager.get().activeProvider.value
                val repo = contentRepository.get()
                
                // Concurrent pre-warm
                launch { repo.getHomeGrid(provider).firstOrNull() }
                launch { repo.getContinueWatching().firstOrNull() }
                launch { top200Repository.get().getRandom5() }
                
                AppLogger.d("Prewarm", "Aggressive startup prewarm launched for ${provider.name}")
            } catch (e: Exception) {
                AppLogger.w("Prewarm", "Startup prewarm error: ${e.message}")
            }
        }

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
                            try {
                                val provider = providerManager.get().activeProvider.value
                                contentRepository.get().getHomeGrid(provider).firstOrNull()
                            } catch (_: Exception) { }
                        }
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        AppLogger.d("ProcessLifecycle", "App moved to background")
                        prewarmScope.launch(Dispatchers.IO) {
                            htmlHttpClient.get().shutdown()
                            try {
                                val overlayDir = java.io.File(codeCacheDir, ".overlay")
                                if (overlayDir.exists()) {
                                    overlayDir.deleteRecursively()
                                    AppLogger.d("UkrtvApplication", "Cleared stale DEX overlay cache for next launch")
                                }
                            } catch (_: Exception) {}
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
            scheduleSeriesIndexUpdate()
            
            delay(10000)
            try {
                val repo = contentRepository.get()
                val provider = providerManager.get().activeProvider.value
                if (repo.isHomeCacheStale(provider.name)) {
                    AppLogger.d("Prewarm", "Home cache stale, refreshing for ${provider.name}")
                    repo.getTmdbTrends(provider)
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

    private fun scheduleSeriesIndexUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val indexWork = PeriodicWorkRequestBuilder<ua.ukrtv.app.worker.SeriesIndexUpdateWorker>(
            24, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "series_index_update",
            ExistingPeriodicWorkPolicy.KEEP,
            indexWork
        )
        AppLogger.i("UkrtvApplication", "Series index update scheduled every 24 hours")
    }

    private fun clearCaches() {
        imageLoader?.memoryCache?.clear()
        providerManager.get().clearCaches()
        try { htmlHttpClient.get().clearMemoryCache() } catch(_: Exception) {}
        try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val existing = imageLoader
        if (existing != null) return existing
        // Never hand out a throwaway loader: Coil caches the first factory result as the
        // singleton, so a temp loader would permanently shadow the fully-configured one
        // (this is what produced the second "newImageLoader requested" in the log).
        AppLogger.d("UkrtvApplication", "newImageLoader requested (building primary synchronously)")
        return buildImageLoader(context, getDeviceClass(context), hasMediatekChipset(), reuseCurrent = true)
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
        val memClass = if (cachedMemoryClass > 0) cachedMemoryClass else (context.getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
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
                add(SvgDecoder.Factory())
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
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(if (deviceClass == DeviceClass.HIGH) 100 else 0)
            .build()
            .also { if (reuseCurrent) {
                AppLogger.d("UkrtvApplication", "ImageLoader initialized for class $deviceClass")
                imageLoader = it
            } }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val isComplete = level >= TRIM_MEMORY_COMPLETE
        val isRunningCritical = level == TRIM_MEMORY_RUNNING_CRITICAL
        val isRunningModerate = level == TRIM_MEMORY_RUNNING_MODERATE
        val isRunningLow = level == TRIM_MEMORY_RUNNING_LOW
        val isUiHidden = level == TRIM_MEMORY_UI_HIDDEN

        when {
            isComplete -> {
                AppLogger.d("Memory", "COMPLETE memory pressure (level $level): trimming Coil cache by half (disk caches preserved)")
                imageLoader?.memoryCache?.let { it.trimToSize((it.size * 0.5).toLong()) }
            }
            isRunningCritical -> {
                AppLogger.d("Memory", "RUNNING_CRITICAL memory pressure (level $level): trimming Coil cache by 50%")
                imageLoader?.memoryCache?.let { cache ->
                    cache.trimToSize((cache.size * 0.5).toLong())
                }
                try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
                try { htmlHttpClient.get().clearMemoryCache() } catch(_: Exception) {}
            }
            isRunningModerate -> {
                AppLogger.d("Memory", "MODERATE memory pressure (level $level): trimming Coil cache by half")
                imageLoader?.memoryCache?.let { cache ->
                    cache.trimToSize((cache.size * 0.5).toLong())
                }
                try { htmlHttpClient.get().clearMemoryCache() } catch(_: Exception) {}
                try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
            }
            isRunningLow -> {
                AppLogger.d("Memory", "LOW memory pressure (level $level): clearing L2 caches only")
                try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
                try { htmlHttpClient.get().clearMemoryCache() } catch(_: Exception) {}
            }
            isUiHidden -> {
                AppLogger.d("Memory", "UI hidden (level $level): minimal cleanup")
                try { contentRepository.get().clearTrendsCache() } catch(_: Exception) {}
            }
        }
    }
}
