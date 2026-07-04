package ua.ukrtv.app.navigation

import java.net.URLEncoder

object AppNavigation {
    const val HOME = "home"
    const val SEARCH = "search?q={q}"
    const val TOP_200 = "top_200"
    const val DETAIL = "detail/{id}?url={url}"
    const val PLAYER = "player/{id}/{title}?url={url}&season={season}&episode={episode}&poster={poster}"
    const val SEASONS = "seasons/{id}?url={url}&title={title}&season={season}&episode={episode}&voiceover={voiceover}"

    fun searchRoute(query: String = ""): String {
        if (query.isEmpty()) return "search?q="
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return "search?q=$encodedQuery"
    }

    fun detailRoute(id: String, url: String): String {
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        return "detail/$encodedId?url=$encodedUrl"
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

    fun seasonsRoute(id: String, url: String, title: String, season: Int? = null, episode: Int? = null, voiceover: String? = null): String {
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val sb = StringBuilder("seasons/$encodedId?url=$encodedUrl&title=$encodedTitle")
        season?.let { sb.append("&season=$it") }
        episode?.let { sb.append("&episode=$it") }
        voiceover?.let { sb.append("&voiceover=${URLEncoder.encode(it, "UTF-8")}") }
        return sb.toString()
    }
}
