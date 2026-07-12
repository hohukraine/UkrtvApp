package ua.ukrtv.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.StrictMode
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class UkrtvApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    companion object {
        val appStartTime = System.nanoTime()
    }

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var providerManager: Lazy<ProviderManager>

    @Inject
    lateinit var contentRepository: Lazy<ua.ukrtv.app.data.repository.ContentRepository>

    @Inject
    lateinit var htmlHttpClient: Lazy<ua.ukrtv.app.data.network.HtmlHttpClient>

    @Inject
    lateinit var workerFactory: dagger.Lazy<androidx.hilt.work.HiltWorkerFactory>

    private var imageLoader: ImageLoader? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()

    override fun onCreate() {
        super.onCreate()
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            AppLogger.d("Startup", "Hilt init: ${(System.nanoTime() - appStartTime) / 1_000_000}ms")
        }
        AppLogger.init(this)
        CrashReporter.init(this)
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            AppLogger.d("Startup", "AppLogger+Crash init: ${(System.nanoTime() - appStartTime) / 1_000_000}ms")
        }
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            Handler(mainLooper).postDelayed({
                StrictMode.setVmPolicy(
                    StrictMode.VmPolicy.Builder()
                        .detectLeakedSqlLiteObjects()
                        .detectLeakedClosableObjects()
                        .detectActivityLeaks()
                        .detectLeakedRegistrationObjects()
                        .penaltyLog()
                        .build()
                )
            }, 5000)
        }
        val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        AppLogger.d("ProcessLifecycle", "App moved to foreground (since class load: ${(System.nanoTime() - appStartTime) / 1_000_000}ms)")
                        htmlHttpClient.get().restart()
                        prewarmScope.launch {
                            delay(2000)
                            try {
                                val provider = providerManager.get().activeProvider.value
                                contentRepository.get().getHomeGrid(provider).firstOrNull()
                                AppLogger.d("Prewarm", "HomeCache prewarm completed for ${provider.name}")
                            } catch (_: Exception) { }
                        }
                        prewarmScope.launch {
                            delay(4000)
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
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        AppLogger.d("ProcessLifecycle", "App moved to background")
                        prewarmScope.launch {
                            htmlHttpClient.get().shutdown()
                            imageLoader?.memoryCache?.clear()
                        }
                    }
                    else -> {}
                }
            }
        )
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            AppLogger.d("Startup", "App.onCreate done: ${(System.nanoTime() - appStartTime) / 1_000_000}ms")
        }

        scheduleCatalogUpdate()
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
    }

    override fun newImageLoader(): ImageLoader {
        val hardware = getDeviceClass(this)
        return buildImageLoader(this, hardware, hasMediatekChipset(), reuseCurrent = false)
    }

    fun applyImageLoaderFor(deviceClass: DeviceClass, isMediatek: Boolean) {
        Coil.setImageLoader(buildImageLoader(this, deviceClass, isMediatek, reuseCurrent = true))
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
            DeviceClass.HIGH -> 0.15
        }
        val adaptiveSize = (maxHeapBytes * memPct).toInt()
        val diskCacheSize = when (deviceClass) {
            DeviceClass.LOW -> 32L * 1024 * 1024
            DeviceClass.MID -> 64L * 1024 * 1024
            DeviceClass.HIGH -> 128L * 1024 * 1024
        }
        val allowHardware = deviceClass != DeviceClass.LOW && !isMediatek

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(adaptiveSize)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(allowHardware)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .respectCacheHeaders(true)
            .build()
            .also { if (reuseCurrent) imageLoader = it }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            htmlHttpClient.get().shutdown()
            imageLoader?.memoryCache?.clear()
        }
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            imageLoader?.memoryCache?.clear()
            providerManager.get().clearCaches()
        }
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            clearCaches()
        }
    }
}
