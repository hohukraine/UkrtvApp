package ua.ukrtv.app.data.api

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class CloudflareInterceptor @Inject constructor(
    private val cfState: CloudflareState
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val host = request.url.host
        
        if (response.code == 403 || response.code == 503 || response.code == 429) {
            val body = response.peekBody(1024 * 10).string()
            val isCloudflare = body.contains("cloudflare", ignoreCase = true) || 
                              body.contains("challenge-platform", ignoreCase = true) ||
                              body.contains("cf-challenge", ignoreCase = true) ||
                              response.header("Server")?.contains("cloudflare", ignoreCase = true) == true
            
            if (isCloudflare) {
                Log.w("CloudflareInterceptor", "Cloudflare challenge detected for $host (code: ${response.code})")
                cfState.markBlocked(host)
            }
        } else if (response.isSuccessful) {
            cfState.markResolved(host)
        }

        return response
    }
}