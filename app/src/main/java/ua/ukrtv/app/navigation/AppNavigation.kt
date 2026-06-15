package ua.ukrtv.app.navigation

import java.net.URLEncoder

object AppNavigation {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{id}?url={url}"
    const val PLAYER = "player/{id}/{title}?url={url}&season={season}&episode={episode}"

    fun detailRoute(id: String, url: String): String {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        return "detail/$id?url=$encodedUrl"
    }

    fun playerRoute(id: String, title: String, url: String, season: Int? = null, episode: Int? = null): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val s = season ?: -1
        val e = episode ?: -1
        return "player/$id/$encodedTitle?url=$encodedUrl&season=$s&episode=$e"
    }
}
