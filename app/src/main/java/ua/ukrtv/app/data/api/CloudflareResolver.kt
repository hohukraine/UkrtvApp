package ua.ukrtv.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import android.view.View
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class CloudflareResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cfState: CloudflareState,
    private val cookieJar: CookieJar
) {
    private val mutex = Mutex()
    private val hostLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private val crashCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val maxCrashesBeforeLongBlock = 3

    suspend fun resolve(url: String): Boolean {
        val host = try { java.net.URI(url).host } catch (e: Exception) { null } ?: return false

        val crashes = crashCounts[host] ?: 0
        if (crashes >= maxCrashesBeforeLongBlock) {
            Log.w("CloudflareResolver", "Domain $host crashed $crashes times, skipping resolution")
            return false
        }

        val hostMutex = hostLocks.getOrPut(host) { Mutex() }

        return hostMutex.withLock {
            if (!cfState.isBlocked(host)) return@withLock true

            val result = doResolve(url, host)
            if (!result) {
                crashCounts[host] = crashes + 1
                if (crashCounts[host]!! >= maxCrashesBeforeLongBlock) {
                    cfState.markBlocked(host, 30 * 60 * 1000L)
                    Log.w("CloudflareResolver", "Domain $host blocked for 30 min due to repeated crashes")
                }
            } else {
                crashCounts.remove(host)
            }
            result
        }
    }

    private suspend fun doResolve(url: String, host: String): Boolean = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        var isCrashed = false
        var finished = false

        try {
            webView = WebView(context)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                domStorageEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    Log.d("CloudflareResolver", "Page finished: $pageUrl")
                    if (!finished && hasClearanceCookie(host)) {
                        finished = true
                        Log.i("CloudflareResolver", "Successfully resolved challenge for $pageUrl")
                        host?.let { cfState.markResolved(it) }
                        syncCookiesToOkHttp(host)
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detail?.didCrash() == true
                    } else true
                    Log.e("CloudflareResolver", "Renderer process crashed! crashed=$didCrash")
                    isCrashed = true
                    finished = true
                    return true
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e("CloudflareResolver", "Error resolving: $description")
                }
            }

            webView.loadUrl(url)

            val resolved = withTimeoutOrNull(20_000) {
                while (!finished && !isCrashed) {
                    if (cfState.isBlocked(host)) {
                        delay(500)
                    } else {
                        return@withTimeoutOrNull true
                    }
                }
                false
            }

            resolved == true
        } catch (e: Exception) {
            Log.e("CloudflareResolver", "Exception during resolve: ${e.message}")
            false
        } finally {
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    private fun hasClearanceCookie(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val cookieManager = CookieManager.getInstance()
        val cookieFromHost = cookieManager.getCookie("https://$host")
        val cookieFromChallenge = cookieManager.getCookie("https://challenges.cloudflare.com")
        return cookieFromHost?.contains("cf_clearance") == true ||
                cookieFromHost?.contains("__cf_bm") == true ||
                cookieFromChallenge?.contains("cf_clearance") == true ||
                cookieFromChallenge?.contains("__cf_bm") == true
    }

    private fun syncCookiesToOkHttp(host: String?) {
        if (host.isNullOrBlank()) return
        try {
            val url = "https://$host".toHttpUrl()
            val webCookies = CookieManager.getInstance().getCookie(url.toString())
            if (!webCookies.isNullOrEmpty()) {
                val cookies = webCookies.split(';').mapNotNull { cookieStr ->
                    val parts = cookieStr.trim().split('=', limit = 2)
                    if (parts.size == 2 && parts[0].isNotEmpty()) {
                        try {
                            Cookie.Builder()
                                .name(parts[0])
                                .value(parts[1])
                                .domain(host)
                                .path("/")
                                .build()
                        } catch (e: Exception) { null }
                    } else null
                }
                if (cookies.isNotEmpty()) {
                    cookieJar.saveFromResponse(url, cookies)
                    android.util.Log.i("CloudflareResolver", "Synced ${cookies.size} cookies to OkHttp for $host")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudflareResolver", "Failed to sync cookies for $host: ${e.message}")
        }
    }
}