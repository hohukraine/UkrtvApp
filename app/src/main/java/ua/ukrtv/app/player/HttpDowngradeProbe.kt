package ua.ukrtv.app.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.Constants
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downgrades https stream URLs to plain http for hosts that serve the same content over both
 * schemes. External players (VLC) then fetch the playlist over http and never hit the
 * untrusted-certificate dialog. Downgrade happens only when a live probe confirms the http
 * endpoint responds AND the (HLS/MPD) playlist body references no absolute https URLs.
 */
@Singleton
class HttpDowngradeProbe internal constructor(
    private val okHttpClient: OkHttpClient,
    private val problemHosts: List<String>
) {

    @Inject
    constructor(okHttpClient: OkHttpClient) : this(okHttpClient, DEFAULT_PROBLEM_HOSTS)

    private val probeClient = okHttpClient.newBuilder()
        .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, Boolean>()

    suspend fun maybeDowngrade(url: String, streamType: StreamType, referer: String): String {
        if (!url.startsWith("https://")) return url
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return url
        if (problemHosts.none { host.contains(it) }) return url

        cache[host]?.let { return if (it) toHttp(url) else url }

        val decision = withContext(Dispatchers.IO) { probe(url, streamType, referer) }
        cache[host] = decision
        return if (decision) toHttp(url) else url
    }

    private fun probe(url: String, streamType: StreamType, referer: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(toHttp(url))
                .header("User-Agent", Constants.USER_AGENT)
                .header("Accept", "*/*")
            if (referer.isNotBlank()) request.header("Referer", referer)

            probeClient.newCall(request.build()).execute().use { response ->
                if (!response.isSuccessful) return false
                if (streamType == StreamType.HLS || streamType == StreamType.MPD) {
                    val body = response.body?.string() ?: return false
                    if (body.contains("https://")) return false
                }
                true
            }
        } catch (e: Exception) {
            AppLogger.d("HttpDowngrade", "probe failed for $url: ${e.message}")
            false
        }
    }

    private fun toHttp(url: String): String = "http://" + url.removePrefix("https://")

    companion object {
        private const val PROBE_TIMEOUT_MS = 4_000L
        private val DEFAULT_PROBLEM_HOSTS = listOf("ashdi", "hdvb", "vidmoly", "mcloud", "uakino", "uaflix", "uafix")
    }
}
