package ua.ukrtv.app.data.network

import okhttp3.Request
import ua.ukrtv.app.Constants

class RequestHeaders(private val userAgent: String = Constants.USER_AGENT) {

    fun applyHeaders(builder: Request.Builder, referer: String?, isAjax: Boolean) {
        builder.header("User-Agent", userAgent)
        builder.header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
        builder.header("Connection", "keep-alive")

        if (isAjax) {
            builder.header("X-Requested-With", "XMLHttpRequest")
            builder.header("Accept", "application/json, text/javascript, */*; q=0.01")
        } else {
            builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        }

        referer?.let {
            builder.header("Referer", it)
            try {
                val uri = java.net.URI(it)
                builder.header("Origin", "${uri.scheme}://${uri.host}")
            } catch (_: Exception) {
            }
        }
    }
}

