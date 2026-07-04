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
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.local.AppDatabase
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.local.dao.WatchlistDao
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.network.WebpToJpegInterceptor
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.util.hasMediatekChipset
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
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
            .fallbackToDestructiveMigration().build()
    }

    @Provides @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides @Singleton
    fun provideHtmlCacheDao(database: AppDatabase): HtmlCacheDao = database.htmlCacheDao()

    @Provides @Singleton
    fun provideWatchlistDao(database: AppDatabase): WatchlistDao = database.watchlistDao()

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

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        
        val cacheSize = 50L * 1024 * 1024
        val cache = Cache(context.cacheDir.resolve("http_cache"), cacheSize)

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .cookieJar(cookieJar)
            .cache(cache)
            .dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 5 })
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectionSpecs(listOf(
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
}
