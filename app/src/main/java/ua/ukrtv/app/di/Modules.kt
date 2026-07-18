package ua.ukrtv.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.local.AppDatabase
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.local.dao.WatchlistDao
import ua.ukrtv.app.data.repository.CatalogIndexBuilder
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.network.WebpToJpegInterceptor
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.util.hasMediatekChipset
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object Modules {

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ukrtv_db")
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .build()
    }

    @Provides @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides @Singleton
    fun provideHtmlCacheDao(database: AppDatabase): HtmlCacheDao = database.htmlCacheDao()

    @Provides @Singleton
    fun provideWatchlistDao(database: AppDatabase): WatchlistDao = database.watchlistDao()

    @Provides @Singleton
    fun provideWatchProgressDao(database: AppDatabase): WatchProgressDao = database.watchProgressDao()

    @Provides @Singleton
    fun provideCatalogIndexDao(database: AppDatabase): CatalogIndexDao = database.catalogIndexDao()

    @Provides @Singleton
    fun provideCatalogIndexBuilder(
        htmlHttpClient: HtmlHttpClient,
        catalogIndexDao: CatalogIndexDao
    ): CatalogIndexBuilder = CatalogIndexBuilder(htmlHttpClient, catalogIndexDao)

    @Provides @Singleton
    fun provideCatalogRepository(
        @ApplicationContext context: Context,
        catalogIndexDao: CatalogIndexDao,
        builder: CatalogIndexBuilder
    ): CatalogRepository = CatalogRepository(context, catalogIndexDao, builder)

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides @Singleton
    fun provideCookieJar(): CookieJar = SimpleCookieJar()

    class SimpleCookieJar : CookieJar {
        private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.computeIfAbsent(url.host) { java.util.concurrent.ConcurrentHashMap() }
            cookies.forEach { cookie ->
                if (cookie.expiresAt > System.currentTimeMillis()) {
                    hostCookies[cookie.name] = cookie
                } else {
                    hostCookies.remove(cookie.name)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host]?.values?.toList() ?: emptyList()
        }
    }

    @Provides @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cookieJar: CookieJar
    ): OkHttpClient {
        val isLowDevice = getDeviceClass(context) == ua.ukrtv.app.util.DeviceClass.LOW
        val isMediatek = hasMediatekChipset()

        val cacheSize = 50L * 1024 * 1024
        val cache = Cache(context.cacheDir.resolve("http_cache"), cacheSize)

        val (sslSocketFactory, trustManager) = createCompositeSslSocketFactory(context)

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .cache(cache)
            .dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 5 })
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .sslSocketFactory(sslSocketFactory, trustManager)
            .connectionSpecs(listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT
            ))
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val builder = request.newBuilder()

                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", Constants.USER_AGENT)
                }

                // WebP → JPEG for old TV Skia compatibility
                if (isLowDevice || isMediatek || url.contains(".webp") || url.contains("format=webp")) {
                    builder.header("Accept", "image/avif,image/jpeg,image/png,*/*;q=0.5")
                }

                chain.proceed(builder.build())
            }
            .addInterceptor(WebpToJpegInterceptor())
            .connectTimeout(Constants.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private data class SslConfig(val socketFactory: javax.net.ssl.SSLSocketFactory, val trustManager: X509TrustManager)

    private fun createCompositeSslSocketFactory(context: Context): SslConfig {
        val defaultTrustManager = run {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            tmf.trustManagers.first() as X509TrustManager
        }

        data class CaCert(val alias: String, val rawId: Int)
        val caCertificates = listOf(
            CaCert("gts_root_r4", ua.ukrtv.app.R.raw.gts_root_r4),
            CaCert("isrg_root_x1", ua.ukrtv.app.R.raw.isrg_root_x1),
            CaCert("isrg_root_x2", ua.ukrtv.app.R.raw.isrg_root_x2),
            CaCert("sectigo_r46", ua.ukrtv.app.R.raw.sectigo_r46)
        )

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val certFactory = CertificateFactory.getInstance("X.509")

        for (ca in caCertificates) {
            try {
                context.resources.openRawResource(ca.rawId).use { stream ->
                    val cert = certFactory.generateCertificate(stream)
                    keyStore.setCertificateEntry(ca.alias, cert)
                }
            } catch (e: Exception) {
                android.util.Log.w("Modules", "Failed to load CA cert: ${ca.alias}", e)
            }
        }

        val ourTrustManager = run {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            tmf.trustManagers.first() as X509TrustManager
        }

        val compositeTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                defaultTrustManager.checkClientTrusted(chain, authType)
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    defaultTrustManager.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    try {
                        ourTrustManager.checkServerTrusted(chain, authType)
                    } catch (e2: CertificateException) {
                        android.util.Log.w("Modules", "Permissive SSL fallback: ${chain.firstOrNull()?.subjectDN}")
                    }
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return defaultTrustManager.acceptedIssuers + ourTrustManager.acceptedIssuers
            }
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(compositeTrustManager), SecureRandom())

        return SslConfig(sslContext.socketFactory, compositeTrustManager)
    }

    @Provides @Singleton
    fun provideTvRecommendationManager(@ApplicationContext context: Context): ua.ukrtv.app.tv.TvRecommendationManager =
        ua.ukrtv.app.tv.TvRecommendationManager(context)

    @Provides @Singleton
    fun provideHtmlHttpClient(
        okHttpClient: OkHttpClient,
        htmlCacheDao: HtmlCacheDao
    ): HtmlHttpClient = HtmlHttpClient(
        okHttpClient = okHttpClient,
        htmlCacheDao = htmlCacheDao,
        tag = "HtmlHttpClient"
    )

    @Provides @Singleton
    fun providePerformancePreferences(@ApplicationContext context: Context): ua.ukrtv.app.util.PerformancePreferences = ua.ukrtv.app.util.PerformancePreferences(context)

    @Provides @Singleton
    fun providePlayerPreferences(@ApplicationContext context: Context): ua.ukrtv.app.util.PlayerPreferences = ua.ukrtv.app.util.PlayerPreferences(context)

    @Provides @Singleton
    fun provideUpdateRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        json: kotlinx.serialization.json.Json
    ): ua.ukrtv.app.data.repository.UpdateRepository = ua.ukrtv.app.data.repository.UpdateRepository(context, okHttpClient, json)
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM catalog_index")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_contentId ON watch_progress(contentId)")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN streamUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN streamType TEXT DEFAULT NULL")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN referer TEXT DEFAULT NULL")
    }
}
