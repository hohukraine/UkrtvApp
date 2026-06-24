package ua.ukrtv.app.navigation

import java.net.URLEncoder

object AppNavigation {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{id}?url={url}&type={type}"
    const val PLAYER = "player/{id}/{title}?url={url}&season={season}&episode={episode}&poster={poster}"

    fun detailRoute(id: String, url: String, type: String? = null): String {
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        var route = "detail/$encodedId?url=$encodedUrl"
        if (type != null) route += "&type=$type"
        return route
    }

    fun playerRoute(id: String, title: String, url: String, season: Int? = null, episode: Int? = null, poster: String = ""): String {
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val encodedPoster = URLEncoder.encode(poster, "UTF-8")
        val sb = StringBuilder("player/$encodedId/$encodedTitle?url=$encodedUrl&poster=$encodedPoster")
        season?.let { sb.append("&season=$it") }
        episode?.let { sb.append("&episode=$it") }
        return sb.toString()
    }
}
