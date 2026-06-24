package ua.ukrtv.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.local.AppDatabase
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.network.HtmlHttpClient
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
    fun provideSearchCacheDao(database: AppDatabase): SearchCacheDao = database.searchCacheDao()

    @Provides @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides @Singleton
    fun provideHtmlCacheDao(database: AppDatabase): HtmlCacheDao = database.htmlCacheDao()

    @Provides @Singleton
    fun provideHtmlHttpClient(
        okHttpClient: OkHttpClient,
        cloudflareState: ua.ukrtv.app.data.api.CloudflareState,
        cloudflareResolver: ua.ukrtv.app.data.api.CloudflareResolver,
        htmlCacheDao: HtmlCacheDao
    ): HtmlHttpClient = HtmlHttpClient(
        okHttpClient = okHttpClient,
        cfState = cloudflareState,
        cfResolver = cloudflareResolver,
        htmlCacheDao = htmlCacheDao,
        tag = "HtmlHttpClient"
    )

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    @Provides @Singleton
    fun provideCookieJar(): CookieJar = SimpleCookieJar()

    class SimpleCookieJar : CookieJar {
        private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { java.util.concurrent.ConcurrentHashMap() }
            cookies.forEach { cookie ->
                if (cookie.expiresAt > System.currentTimeMillis()) {
                    hostCookies[cookie.name] = cookie
                } else {
                    hostCookies.remove(cookie.name)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val hostCookies = cookieStore[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            val validCookies = hostCookies.values.filter { it.expiresAt > now }
            if (validCookies.size != hostCookies.size) {
                val iterator = hostCookies.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value.expiresAt <= now) iterator.remove()
                }
            }
            return validCookies
        }
    }

    @Provides @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cookieJar: CookieJar,
        cloudflareInterceptor: ua.ukrtv.app.data.api.CloudflareInterceptor
    ): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        val cacheSize = 10L * 1024 * 1024
        val cache = okhttp3.Cache(context.cacheDir.resolve("http_cache"), cacheSize)
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 15
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .cookieJar(cookieJar)
            .cache(cache)
            .dispatcher(dispatcher)
            .addInterceptor(cloudflareInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", Constants.USER_AGENT)
                }
                if (request.url.host.contains("uakino")) {
                    if (request.header("Accept") == null) {
                        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }
                }
                chain.proceed(builder.build())
            }
            .connectTimeout(Constants.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun provideDataSourceFactory(okHttpClient: OkHttpClient): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient)
    }

}
