package ua.ukrtv.app.data.network

import ua.ukrtv.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import ua.ukrtv.app.Constants
import java.security.cert.CertPathValidatorException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLException

private const val RATE_LIMIT_COOLDOWN_MS = 30_000L

class HtmlHttpClient(
    private val okHttpClient: OkHttpClient,
    private val htmlCacheDao: ua.ukrtv.app.data.local.dao.HtmlCacheDao,
    private val tag: String = "HtmlHttpClient",
) {
    private val ashdiClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
    private val userAgent: String = Constants.USER_AGENT
    private var refreshJob = SupervisorJob()
    private var refreshScope = CoroutineScope(Dispatchers.IO + refreshJob)
    private val hostSemaphores = ConcurrentHashMap<String, Semaphore>()
    private val inflightRefreshes = ConcurrentHashMap<String, Boolean>()

    /** Hosts that recently returned HTTP 429; all requests to them fail fast until the timestamp. */
    private val hostCooldownUntil = ConcurrentHashMap<String, Long>()

    private fun isHostCoolingDown(host: String?): Boolean {
        if (host == null) return false
        val until = hostCooldownUntil[host] ?: return false
        if (System.currentTimeMillis() >= until) {
            hostCooldownUntil.remove(host)
            return false
        }
        return true
    }

    private fun coolDownHost(host: String?) {
        if (host == null) return
        val until = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
        hostCooldownUntil[host] = until
        AppLogger.w(tag, "HTTP 429: cooling down $host for ${RATE_LIMIT_COOLDOWN_MS}ms")
    }

    private fun getHostSemaphore(host: String): Semaphore {
        return hostSemaphores.computeIfAbsent(host) { Semaphore(2) }
    }

    fun shutdown() {
        refreshJob.cancel()
        htmlCache.clear()
    }

    fun restart() {
        refreshJob.cancel()
        refreshJob = SupervisorJob()
        refreshScope = CoroutineScope(Dispatchers.IO + refreshJob)
    }

    fun clearMemoryCache() {
        htmlCache.clear()
        hostSemaphores.clear()
        inflightRefreshes.clear()
        hostCooldownUntil.clear()
    }

    private fun isSslError(e: Exception): Boolean {
        var cause: Throwable = e
        while (cause != cause.cause && cause.cause != null) {
            cause = cause.cause!!
        }
        return cause is SSLException || cause is CertPathValidatorException
    }

    private val htmlCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, String>>(60, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean {
                return size > 50
            }
        }
    )

    private fun applyHeaders(builder: Request.Builder, referer: String?, isAjax: Boolean) {
        val urlStr = try { builder.build().url.toString() } catch (_: Exception) { "" }
        builder.header("User-Agent", userAgent)
        builder.header("Accept-Language", "ru-RU,ru;q=0.9,uk-UA;q=0.8,uk;q=0.7,en-US;q=0.6,en;q=0.5")
        builder.header("Connection", "keep-alive")
        builder.header("Cache-Control", "max-age=0")

        builder.header("sec-ch-ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"125\", \"Chromium\";v=\"125\"")
        builder.header("sec-ch-ua-mobile", "?0")
        builder.header("sec-ch-ua-platform", "\"Windows\"")

        if (urlStr.contains("ashdi")) {
            if (isAjax) {
                builder.header("X-Requested-With", "XMLHttpRequest")
                builder.header("Accept", "*/*")
                builder.header("Sec-Fetch-Dest", "empty")
                builder.header("Sec-Fetch-Mode", "cors")
                builder.header("Sec-Fetch-Site", "cross-site")
            } else {
                builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                builder.header("Upgrade-Insecure-Requests", "1")
                builder.header("Sec-Fetch-Dest", "iframe")
                builder.header("Sec-Fetch-Mode", "navigate")
                builder.header("Sec-Fetch-Site", "cross-site")
            }
            val ref = referer ?: "https://uakino.best/"
            builder.header("Referer", ref)
            try {
                val uri = java.net.URI(ref)
                builder.header("Origin", "${uri.scheme}://${uri.host}")
            } catch (_: Exception) {
                builder.header("Origin", "https://uakino.best")
            }
            return
        }

        if (isAjax) {
            builder.header("X-Requested-With", "XMLHttpRequest")
            builder.header("Accept", "application/json, text/javascript, */*; q=0.01")
            builder.header("Sec-Fetch-Dest", "empty")
            builder.header("Sec-Fetch-Mode", "cors")
            builder.header("Sec-Fetch-Site", "same-origin")
        } else {
            builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            builder.header("Upgrade-Insecure-Requests", "1")
            builder.header("Sec-Fetch-Dest", "document")
            builder.header("Sec-Fetch-Mode", "navigate")
            builder.header("Sec-Fetch-Site", "same-origin")
            builder.header("Sec-Fetch-User", "?1")
        }

        referer?.let {
            builder.header("Referer", it)
            try {
                val uri = java.net.URI(it)
                builder.header("Origin", "${uri.scheme}://${uri.host}")
            } catch (e: Exception) {
                AppLogger.w("HtmlHttpClient", "Failed to parse referer URI: ${e.message}")
            }
        }
    }

    private suspend fun <T> runWithRetries(
        tag: String,
        host: String? = null,
        skipRateLimitRetry: Boolean = false,
        block: suspend (attempt: Int) -> T,
        onRetryDelay: suspend (delayTimeMs: Long, attempt: Int) -> Unit = { d, _ -> delay(d) }
    ): T? {
        var lastError: Exception? = null
        val maxRetries = Constants.MAX_RETRIES

        repeat(maxRetries) { attempt ->
            try {
                return block(attempt)
            } catch (e: Exception) {
                lastError = e

                if (attempt >= maxRetries - 1) return null

                if (e.message?.contains("404") == true) {
                    AppLogger.w(tag, "HTTP 404 is permanent, skipping retries")
                    return null
                }

                if (isSslError(e)) {
                    AppLogger.w(tag, "SSL error is permanent, skipping retries: host=$host ${e.message}")
                    return null
                }

                if (skipRateLimitRetry && e.message?.contains("429") == true) {
                    // In bulk series-fetch paths a 5s/10s backoff per blocked season blows
                    // the whole resolution budget, so treat 429 as "skip now" instead.
                    coolDownHost(host)
                    AppLogger.w(tag, "HTTP 429 skipped (rate-limit retry disabled): $host")
                    return null
                }

                if (e.message?.contains("429") == true) {
                    // Even on a normal retry path, stop hammering a host that is rate limiting
                    // us: the 429s escalate into Cloudflare 522s (connection timeouts) and the
                    // app burns its whole resolution budget.
                    coolDownHost(host)
                }

                val delayTime = when {
                    e.message?.contains("429") == true -> 5000L * (attempt + 1)
                    e.message?.contains("403") == true || e.message?.contains("503") == true -> 1000L * (attempt + 1)
                    else -> Constants.RETRY_DELAY_MS
                }

                if (e.message?.contains("403") == true) {
                    delay(300 + (Math.random() * 500).toLong()) // Anti-bot jitter
                }

                AppLogger.w(tag, "Retry ${attempt + 1}/$maxRetries (Error: ${e.message}) in ${delayTime}ms")
                onRetryDelay(delayTime, attempt)
            }
        }

        AppLogger.e(tag, "Error after $maxRetries attempts: ${lastError?.message}")
        return null
    }

    suspend fun getHtml(
        url: String,
        referer: String? = null,
        isAjax: Boolean = false,
        skipRateLimitRetry: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = url
        if (!isAjax) {
            synchronized(htmlCache) {
                val cached = htmlCache[cacheKey]
                if (cached != null) {
                    val age = System.currentTimeMillis() - cached.first
                    if (age < Constants.HTML_CACHE_TTL_MS) {
                        return@withContext cached.second
                    }
                    if (age < Constants.HTML_CACHE_STALE_TTL_MS) {
                        if (inflightRefreshes.putIfAbsent(cacheKey, true) == null) {
                            refreshScope.launch {
                                try { fetchAndCacheHtml(url, referer, isAjax, cacheKey) }
                                finally { inflightRefreshes.remove(cacheKey) }
                            }
                        }
                        return@withContext cached.second
                    }
                }
            }

            try {
                val dbCached = htmlCacheDao.getHtml(url)
                if (dbCached != null) {
                    val age = System.currentTimeMillis() - dbCached.timestamp
                    if (age < Constants.HTML_CACHE_TTL_MS) {
                        synchronized(htmlCache) { htmlCache[cacheKey] = dbCached.timestamp to dbCached.content }
                        return@withContext dbCached.content
                    }
                    if (age < Constants.HTML_CACHE_STALE_TTL_MS) {
                        synchronized(htmlCache) { htmlCache[cacheKey] = dbCached.timestamp to dbCached.content }
                        if (inflightRefreshes.putIfAbsent(cacheKey, true) == null) {
                            refreshScope.launch {
                                try { fetchAndCacheHtml(url, referer, isAjax, cacheKey) }
                                finally { inflightRefreshes.remove(cacheKey) }
                            }
                        }
                        return@withContext dbCached.content
                    }
                    htmlCacheDao.delete(url)
                }
            } catch (e: Exception) {
                AppLogger.e(tag, "HTML DB cache read failed", e)
            }
        }

        fetchAndCacheHtml(url, referer, isAjax, cacheKey, skipRateLimitRetry)
    }

    private suspend fun fetchAndCacheHtml(
        url: String,
        referer: String?,
        isAjax: Boolean,
        cacheKey: String,
        skipRateLimitRetry: Boolean = false
    ): String? {
        val host = try { java.net.URI(url).host } catch (e: Exception) {
            AppLogger.w("HtmlHttpClient", "Failed to parse URL host: ${e.message}")
            null
        }
        val semaphore = host?.let { getHostSemaphore(it) }
        if (isHostCoolingDown(host)) {
            AppLogger.w(tag, "Host $host is rate-limit cooling down, skipping: $url")
            return null
        }
        semaphore?.acquire()
        try {
            return withTimeoutOrNull(20_000L) {
                runWithRetries(tag, host = host, skipRateLimitRetry = skipRateLimitRetry, block = { attempt ->
                    val builder = Request.Builder().url(url)
                    applyHeaders(builder, referer, isAjax)
                    val request = builder.build()

                    val client = if (url.contains("ashdi")) ashdiClient else okHttpClient
                    client.newCall(request).execute().use { response ->
                        val responseCode = response.code

                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!isAjax && body != null) {
                                synchronized(htmlCache) {
                                    htmlCache[cacheKey] = System.currentTimeMillis() to body
                                }
                                try {
                                    htmlCacheDao.insert(ua.ukrtv.app.data.local.entity.HtmlCacheEntity(url, body))
                                } catch (e: Exception) {
                                    AppLogger.e(tag, "HTML DB cache write failed", e)
                                }
                            }
                            return@runWithRetries body
                        } else {
                            val msg = response.message
                            response.body?.close() // Explicitly close body on failure
                            AppLogger.w(tag, "Failed GET $url: $responseCode $msg")
                            throw Exception("HTTP $responseCode")
                        }
                    }
                })
            }
        } finally {
            semaphore?.release()
        }
    }

    suspend fun postHtml(
        url: String,
        body: RequestBody,
        referer: String? = null,
        isAjax: Boolean = false,
        skipRateLimitRetry: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (isHostCoolingDown(host)) {
            AppLogger.w(tag, "Host $host is rate-limit cooling down, skipping POST: $url")
            return@withContext null
        }
        withTimeoutOrNull(20_000L) {
            runWithRetries(tag, host = host, skipRateLimitRetry = skipRateLimitRetry, block = { attempt ->
                val builder = Request.Builder().url(url)
                applyHeaders(builder, referer, isAjax)
                val request = builder.post(body).build()

                AppLogger.d(tag, "POST attempt=${attempt + 1} url=$url referer=$referer isAjax=$isAjax")

                okHttpClient.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    val responseBody = response.body?.string()

                    val preview = responseBody?.take(600)
                    if (responseBody?.contains("ERR_") == true) {
                         AppLogger.w(tag, "POST response code=$responseCode for $url | Result: ${responseBody.take(100)}")
                    } else {
                         AppLogger.d(tag, "POST response code=$responseCode for $url | preview=${preview?.replace("\n", " ")}")
                    }

                    if (response.isSuccessful) {
                        return@runWithRetries responseBody
                    } else {
                        val msg = response.message
                        response.body?.close() // Explicitly close body on failure
                        AppLogger.w(tag, "Failed POST $url: $responseCode $msg")
                        throw Exception("HTTP $responseCode")
                    }
                }
            })
        }
    }
}
