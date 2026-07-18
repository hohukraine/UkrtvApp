package ua.ukrtv.app.navigation

import android.net.Uri
import ua.ukrtv.app.data.providers.ContentCategory

object AppNavigation {
    const val HOME = "home"
    const val SEARCH = "search?q={q}"
    const val TOP_200 = "top_200"
    const val TRENDS_GRID = "trends_grid"
    const val DETAIL = "detail/{id}?url={url}&alternate={alternate}"
    const val PLAYER = "player/{id}/{title}?url={url}&season={season}&episode={episode}&poster={poster}"
    const val SETTINGS = "settings"
    const val CATEGORY_GRID = "category_grid?category={category}"

    fun searchRoute(query: String = ""): String {
        if (query.isEmpty()) return "search?q="
        val encodedQuery = Uri.encode(query, null)
        return "search?q=$encodedQuery"
    }

    fun detailRoute(id: String, url: String, alternateUrl: String? = null): String {
        val encodedId = Uri.encode(id, null)
        val encodedUrl = Uri.encode(url, null)
        val base = "detail/$encodedId?url=$encodedUrl"
        return if (alternateUrl != null) "$base&alternate=${Uri.encode(alternateUrl, null)}" else base
    }

    fun categoryGridRoute(categoryKey: String): String {
        return "category_grid?category=$categoryKey"
    }

    fun playerRoute(id: String, title: String, url: String, season: Int? = null, episode: Int? = null, poster: String = ""): String {
        val encodedId = Uri.encode(id, null)
        val encodedTitle = Uri.encode(title, null)
        val encodedUrl = Uri.encode(url, null)
        val encodedPoster = Uri.encode(poster, null)
        val sb = StringBuilder("player/$encodedId/$encodedTitle?url=$encodedUrl&poster=$encodedPoster")
        season?.let { sb.append("&season=$it") }
        episode?.let { sb.append("&episode=$it") }
        return sb.toString()
    }

}
