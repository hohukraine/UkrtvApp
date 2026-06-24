package ua.ukrtv.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.api.CloudflareResolver
import ua.ukrtv.app.data.api.CloudflareState
import ua.ukrtv.app.util.AppLogger

import java.util.Collections

class HtmlHttpClient(
    private val okHttpClient: OkHttpClient,
    private val cfState: CloudflareState?,
    private val cfResolver: CloudflareResolver?,
    private val htmlCacheDao: ua.ukrtv.app.data.local.dao.HtmlCacheDao,
    private val tag: String = "HtmlHttpClient",
) {
    private val userAgent: String = Constants.USER_AGENT

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
        builder.header("Cache-Control", "no-cache")
        builder.header("Pragma", "no-cache")

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
            } catch (_: Exception) {}
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
                    Log.w(tag, "HTTP 404 is permanent, skipping retries")
                    return null
                }

                val delayTime = when {
                    e.message?.contains("429") == true -> 3000L * (attempt + 1)
                    e.message?.contains("403") == true || e.message?.contains("503") == true -> 500L
                    else -> Constants.RETRY_DELAY_MS
                }

                Log.w(tag, "Retry ${attempt + 1}/$maxRetries (Error: ${e.message}) in ${delayTime}ms")
                onRetryDelay(delayTime, attempt)
            }
        }

        Log.e(tag, "Error after $maxRetries attempts: ${lastError?.message}")
        return null
    }

    suspend fun getHtml(
        url: String,
        referer: String? = null,
        isAjax: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = url
        if (!isAjax) {
            val cached = htmlCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < Constants.HTML_CACHE_TTL_MS) {
                return@withContext cached.second
            }

            try {
                val dbCached = htmlCacheDao.getHtml(url)
                if (dbCached != null) {
                    val age = System.currentTimeMillis() - dbCached.timestamp
                    if (age < 3600_000) {
                        htmlCache[cacheKey] = dbCached.timestamp to dbCached.content
                        return@withContext dbCached.content
                    } else {
                        htmlCacheDao.delete(url)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(tag, "HTML DB cache read failed", e)
            }
        }

        withTimeoutOrNull(20_000L) {
            runWithRetries(tag, block = { attempt ->

                val cloudflare = CloudflareHandler(cfState, cfResolver, tag)
                cloudflare.maybeResolve(url)

                val builder = Request.Builder().url(url)
                applyHeaders(builder, referer, isAjax)

                val request = builder.get().build()

                okHttpClient.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    val body = response.body?.string()

                    if (response.isSuccessful) {
                        if (body != null && cloudflare.isCloudflareChallenge(body)) {
                            val host = try { java.net.URI(url).host } catch (_: Exception) { null }
                            if (host != null) cloudflare.markBlocked(host)
                            throw Exception("Cloudflare challenge")
                        }

                        if (!isAjax && body != null) {
                            htmlCache[cacheKey] = System.currentTimeMillis() to body
                            try {
                                htmlCacheDao.insert(ua.ukrtv.app.data.local.entity.HtmlCacheEntity(url, body))
                            } catch (e: Exception) {
                                AppLogger.e(tag, "HTML DB cache write failed", e)
                            }
                        }
                        return@runWithRetries body
                    } else {
                        if (responseCode == 403 || responseCode == 503 || responseCode == 429) {
                            val host = try { java.net.URI(url).host } catch (_: Exception) { null }
                            if (host != null) cloudflare.markBlocked(host)
                        }
                        AppLogger.w(tag, "Failed GET $url: $responseCode ${response.message}")
                        throw Exception("HTTP $responseCode")
                    }
                }
            })
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
                val cloudflare = CloudflareHandler(cfState, cfResolver, tag)
                cloudflare.maybeResolve(url)

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
                        if (responseBody != null && cloudflare.isCloudflareChallenge(responseBody)) {
                            val host = try { java.net.URI(url).host } catch (_: Exception) { null }
                            if (host != null) cloudflare.markBlocked(host)
                            throw Exception("Cloudflare challenge")
                        }
                        return@runWithRetries responseBody
                    } else {
                        if (responseCode == 403 || responseCode == 503 || responseCode == 429) {
                            val host = try { java.net.URI(url).host } catch (_: Exception) { null }
                            if (host != null) cloudflare.markBlocked(host)
                        }
                        Log.w(tag, "Failed POST $url: $responseCode ${response.message}")
                        throw Exception("HTTP $responseCode")
                    }
                }
            })
        }
    }
}
