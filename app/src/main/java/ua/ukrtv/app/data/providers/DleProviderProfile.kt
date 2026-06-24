package ua.ukrtv.app.data.providers

import com.google.gson.annotations.SerializedName

enum class ContentCategory {
    MOVIES, SERIES, ANIME, CARTOONS, CARTOON_SERIES, TRENDS
}

/**
 * Data class for DLE provider configuration, easy to serialize/deserialize.
 */
data class DleProviderProfile(
    @SerializedName("name") val name: String,
    @SerializedName("baseUrl") val baseUrl: String,
    @SerializedName("brandColor") val brandColor: String,
    @SerializedName("logoUrl") val logoUrl: String,
    @SerializedName("selectors") val selectors: Selectors,
    @SerializedName("seriesMarkers") val seriesMarkers: List<String>,
    @SerializedName("categoryPaths") val categoryPaths: Map<ContentCategory, String>,
    @SerializedName("playlistEndpoints") val playlistEndpoints: List<String> = listOf("engine/ajax/playlists.php"),
    @SerializedName("searchEndpointAjax") val searchEndpointAjax: String = "engine/ajax/search.php",
    @SerializedName("searchEndpointNonAjax") val searchEndpointNonAjax: String = "index.php?do=search&subaction=search",
    @SerializedName("idRegexPattern") val idRegexPattern: String = """^(\d+)"""
) {
    val idRegex: Regex get() = Regex(idRegexPattern)

    data class Selectors(
        @SerializedName("item") val item: String,
        @SerializedName("title") val title: String,
        @SerializedName("poster") val poster: String,
        @SerializedName("detailPoster") val detailPoster: String? = null,
        @SerializedName("description") val description: String,
        @SerializedName("year") val year: String? = null,
        @SerializedName("searchLink") val searchLink: String = "a",
        @SerializedName("searchTitle") val searchTitle: String? = "span"
    )
}

// Built-in profiles as fallback or defaults
val UakinoProfile = DleProviderProfile(
    name = "Uakino",
    baseUrl = "https://uakino.best/",
    brandColor = "#ca563f",
    logoUrl = "https://uakino.best/templates/uakino/images/logo.png",
    selectors = DleProviderProfile.Selectors(
        item = ".movie-item, .short-item",
        title = ".movie-title, .short-title",
        poster = "img[data-src], img[src]",
        description = ".full-text, .full-description",
        year = ".movie-desk-item:contains(Рік:) .deck-value, .fi-year"
    ),
    seriesMarkers = listOf("/seriesss/", "/seriali/", "/animeukr/", "/cartoon/", "/cartoons/", "/tv-shows/"),
    playlistEndpoints = emptyList(),
    categoryPaths = mapOf(
        ContentCategory.TRENDS to "find/year/2026/f/sort=rating;desc/",
        ContentCategory.MOVIES to "filmy/online/",
        ContentCategory.SERIES to "seriesss/online/",
        ContentCategory.ANIME to "animeukr/online/",
        ContentCategory.CARTOONS to "cartoon/online/",
        ContentCategory.CARTOON_SERIES to "cartoon/cartoonseries/"
    )
)

val EneyidaProfile = DleProviderProfile(
    name = "Eneyida",
    baseUrl = "https://eneyida.tv/",
    brandColor = "#31C469",
    logoUrl = "https://eneyida.tv/templates/Eneyida/images/logo.png",
    selectors = DleProviderProfile.Selectors(
        item = "article.short",
        title = "a.short_title",
        poster = "a.short_img img",
        detailPoster = ".full_content-poster img",
        description = ".full-text",
        year = ".short_subtitle, .full-right li:contains(Рік), .full-info li:contains(Рік)"
    ),
    seriesMarkers = listOf("/series/", "/cartoon-series/", "/anime-series/", "/serialy/", "/anime/", "/filmi-seriali/"),
    playlistEndpoints = emptyList(),
    categoryPaths = mapOf(
        ContentCategory.TRENDS to "f/sort=rating/order=desc/",
        ContentCategory.MOVIES to "films/",
        ContentCategory.SERIES to "series/",
        ContentCategory.ANIME to "anime/",
        ContentCategory.CARTOONS to "cartoon/",
        ContentCategory.CARTOON_SERIES to "cartoon-series/"
    )
)
