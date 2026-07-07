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
import okhttp3.Request
import okhttp3.RequestBody
import ua.ukrtv.app.Constants
import java.security.cert.CertPathValidatorException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLException

class HtmlHttpClient(
    private val okHttpClient: OkHttpClient,
    private val htmlCacheDao: ua.ukrtv.app.data.local.dao.HtmlCacheDao,
    private val tag: String = "HtmlHttpClient",
) {
    private val userAgent: String = Constants.USER_AGENT
    private val refreshJob = SupervisorJob()
    private val refreshScope = CoroutineScope(Dispatchers.IO + refreshJob)
    private val hostSemaphores = ConcurrentHashMap<String, Semaphore>()
    private val inflightRefreshes = ConcurrentHashMap<String, Boolean>()

    private fun getHostSemaphore(host: String): Semaphore {
        return hostSemaphores.computeIfAbsent(host) { Semaphore(2) }
    }

    fun shutdown() {
        refreshJob.cancel()
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
        builder.header("User-Agent", userAgent)
        builder.header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
        builder.header("Connection", "keep-alive")

        builder.header("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
        builder.header("sec-ch-ua-mobile", "?0")
        builder.header("sec-ch-ua-platform", "\"Windows\"")

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
                    AppLogger.w(tag, "SSL error is permanent, skipping retries: ${e.message}")
                    return null
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
        isAjax: Boolean = false
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

        fetchAndCacheHtml(url, referer, isAjax, cacheKey)
    }

    private suspend fun fetchAndCacheHtml(
        url: String,
        referer: String?,
        isAjax: Boolean,
        cacheKey: String
    ): String? {
        val host = try { java.net.URI(url).host } catch (e: Exception) {
            AppLogger.w("HtmlHttpClient", "Failed to parse URL host: ${e.message}")
            null
        }
        val semaphore = host?.let { getHostSemaphore(it) }
        semaphore?.acquire()
        try {
            return withTimeoutOrNull(20_000L) {
                runWithRetries(tag, block = { attempt ->
                    val builder = Request.Builder().url(url)
                    applyHeaders(builder, referer, isAjax)
                    val request = builder.get().build()

                    okHttpClient.newCall(request).execute().use { response ->
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
                            AppLogger.w(tag, "Failed GET $url: $responseCode ${response.message}")
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
        isAjax: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(20_000L) {
            runWithRetries(tag, block = { attempt ->
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
                        AppLogger.w(tag, "Failed POST $url: $responseCode ${response.message}")
                        throw Exception("HTTP $responseCode")
                    }
                }
            })
        }
    }
}
