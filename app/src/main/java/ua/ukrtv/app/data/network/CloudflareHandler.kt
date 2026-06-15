package ua.ukrtv.app.data.network

import android.util.Log
import ua.ukrtv.app.data.api.CloudflareResolver
import ua.ukrtv.app.data.api.CloudflareState

class CloudflareHandler(
    private val cfState: CloudflareState?,
    private val cfResolver: CloudflareResolver?,
    private val tag: String,
) {

    fun isCloudflareChallenge(body: String): Boolean {
        return body.contains("cf-challenge") ||
            body.contains("ray id:") ||
            body.contains("Checking your browser before accessing")
    }

    suspend fun maybeResolve(url: String): Boolean {
        val host = try { java.net.URI(url).host } catch (_: Exception) { null }
        if (host != null && cfState?.isBlocked(host) == true && cfResolver != null) {
            Log.i(tag, "Domain $host is blocked by Cloudflare, attempting resolution...")
            val resolved = cfResolver.resolve(url)
            if (!resolved) {
                Log.e(tag, "Cloudflare resolution failed for $host")
            }
            return resolved
        }
        return false
    }

    fun markBlocked(host: String) {
        cfState?.markBlocked(host, 1000)
    }
}

