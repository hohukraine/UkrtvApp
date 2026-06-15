package ua.ukrtv.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import ua.ukrtv.app.Constants
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import ua.ukrtv.app.data.network.CloudflareHandler
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.network.RequestHeaders
import ua.ukrtv.app.data.network.RetryPolicy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import ua.ukrtv.app.data.api.TmdbApi
import ua.ukrtv.app.data.api.UakinoApi
import ua.ukrtv.app.data.api.EneyidaApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTmdbApi(okHttpClient: OkHttpClient, gson: Gson): TmdbApi {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(TmdbApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUakinoApi(okHttpClient: OkHttpClient, gson: Gson): UakinoApi {
        return Retrofit.Builder()
            .baseUrl("https://uakino.best/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(UakinoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideEneyidaApi(okHttpClient: OkHttpClient, gson: Gson): EneyidaApi {
        return Retrofit.Builder()
            .baseUrl("https://eneyida.tv/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EneyidaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy = RetryPolicy()

    @Provides
    @Singleton
    fun provideRequestHeaders(): RequestHeaders = RequestHeaders()

    @Provides
    @Singleton
    fun provideHtmlHttpClient(
        okHttpClient: OkHttpClient,
        requestHeaders: RequestHeaders,
        retryPolicy: RetryPolicy,
        cloudflareState: ua.ukrtv.app.data.api.CloudflareState,
        cloudflareResolver: ua.ukrtv.app.data.api.CloudflareResolver
    ): HtmlHttpClient = HtmlHttpClient(
        okHttpClient = okHttpClient,
        requestHeaders = requestHeaders,
        retryPolicy = retryPolicy,
        cfState = cloudflareState,
        cfResolver = cloudflareResolver,
        tag = "HtmlHttpClient"
    )


    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideCookieJar(): CookieJar = WebViewSyncCookieJar()

    class WebViewSyncCookieJar : CookieJar {
        private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, List<okhttp3.Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<okhttp3.Cookie>) {
            cookieStore[url.host] = cookies.filter { it.expiresAt > System.currentTimeMillis() }
        }

        override fun loadForRequest(url: HttpUrl): List<okhttp3.Cookie> {
            val host = url.host
            val webCookies = try {
                android.webkit.CookieManager.getInstance().getCookie(url.toString())
            } catch (e: Exception) {
                null
            }
            if (!webCookies.isNullOrEmpty()) {
                val cookies = webCookies.split(';').mapNotNull { cookieStr ->
                    val parts = cookieStr.trim().split('=', limit = 2)
                    if (parts.size == 2 && parts[0].isNotEmpty()) {
                        try {
                            okhttp3.Cookie.Builder()
                                .name(parts[0])
                                .value(parts[1])
                                .domain(host)
                                .path("/")
                                .build()
                        } catch (e: Exception) { null }
                    } else null
                }.filter { it.expiresAt > System.currentTimeMillis() }
                
                if (cookies.isNotEmpty()) {
                    cookieStore[host] = cookies
                    return cookies
                }
            }
            return cookieStore[host]?.filter { it.expiresAt > System.currentTimeMillis() } ?: listOf()
        }

        fun syncFromWebView(host: String) {
            try {
                val url = "https://$host".toHttpUrl()
                val webCookies = android.webkit.CookieManager.getInstance().getCookie(url.toString())
                if (!webCookies.isNullOrEmpty()) {
                    val cookies = webCookies.split(';').mapNotNull { cookieStr ->
                        val parts = cookieStr.trim().split('=', limit = 2)
                        if (parts.size == 2 && parts[0].isNotEmpty()) {
                            try {
                                okhttp3.Cookie.Builder()
                                    .name(parts[0])
                                    .value(parts[1])
                                    .domain(host)
                                    .path("/")
                                    .build()
                            } catch (e: Exception) { null }
                        } else null
                    }.filter { it.expiresAt > System.currentTimeMillis() }

                    if (cookies.isNotEmpty()) {
                        cookieStore[host] = cookies
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CookieJar", "Failed to sync cookies for $host: ${e.message}")
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
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

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .cookieJar(cookieJar)
            .addInterceptor(cloudflareInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", Constants.USER_AGENT)
                }
                
                if (request.url.host.contains("uakino") || request.url.host.contains("eneyida")) {
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
    @Provides
    @Singleton
    fun provideDataSourceFactory(okHttpClient: OkHttpClient): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient)
    }
}
