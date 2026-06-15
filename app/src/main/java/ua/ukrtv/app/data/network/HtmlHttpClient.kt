package ua.ukrtv.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
// import okhttp3.toRequestBody
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.api.CloudflareResolver
import ua.ukrtv.app.data.api.CloudflareState

import java.util.Collections

class HtmlHttpClient(
    private val okHttpClient: OkHttpClient,
    private val requestHeaders: RequestHeaders,
    private val retryPolicy: RetryPolicy,
    private val cfState: CloudflareState?,
    private val cfResolver: CloudflareResolver?,
    private val tag: String = "HtmlHttpClient",
) {

    private val htmlCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, String>>(15, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean {
                return size > 10
            }
        }
    )

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
        }

        withTimeoutOrNull(45_000L) {
            retryPolicy.runWithRetries(tag, block = { attempt ->

                // Cloudflare resolution attempt
                val cloudflare = CloudflareHandler(cfState, cfResolver, tag)
                cloudflare.maybeResolve(url)

                val builder = Request.Builder().url(url)
                requestHeaders.applyHeaders(builder, referer, isAjax)

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
                        }
                        return@runWithRetries body
                    } else {
                        if (responseCode == 403 || responseCode == 503 || responseCode == 429) {
                            val host = try { java.net.URI(url).host } catch (_: Exception) { null }
                            if (host != null) cloudflare.markBlocked(host)
                        }
                        Log.w(tag, "Failed GET $url: $responseCode ${response.message}")
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
        withTimeoutOrNull(45_000L) {
            retryPolicy.runWithRetries(tag, block = { attempt ->
                val cloudflare = CloudflareHandler(cfState, cfResolver, tag)
                cloudflare.maybeResolve(url)

                val builder = Request.Builder().url(url)
                requestHeaders.applyHeaders(builder, referer, isAjax)
                val request = builder.post(body).build()

                // Debug preview (без повного тіла, щоб не засмітити логи; RequestBody не завжди можна прочитати повторно)
                Log.d(tag, "POST attempt=${attempt + 1} url=$url referer=$referer isAjax=$isAjax")

                okHttpClient.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    val responseBody = response.body?.string()

                    val preview = responseBody?.take(600)
                    Log.d(tag, "POST response code=$responseCode for $url | preview=${preview?.replace("\n", " ")}")

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

