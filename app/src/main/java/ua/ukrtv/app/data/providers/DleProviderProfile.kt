package ua.ukrtv.app.data.providers

enum class ContentCategory {
    MOVIES, SERIES, ANIME, CARTOONS, CARTOON_SERIES, TRENDS
}

enum class PlaylistType {
    AJAX_POST,
    IFRAME_JSON,
    DIRECT_URL
}

data class ExtractionRule(
    val selector: String,
    val terms: List<String> = emptyList(),
    val attribute: String? = null,
    val regex: Regex? = null,
    val priority: Int = 0
)

data class DleProviderProfile(
    val name: String,
    val baseUrl: String,
    val brandColor: String,
    val selectors: Selectors,
    val categoryPaths: Map<ContentCategory, String>,
    val metadataRules: Map<String, List<ExtractionRule>> = emptyMap(),
    val nonGenreLabels: Set<String> = emptySet()
) {
    data class Selectors(
        val item: String,
        val title: String,
        val poster: String,
        val cardItem: String = item,
        val cardTitle: String = title,
        val cardPoster: String = poster,
        val cardLink: String = "a[href]",
        val detailPoster: String? = null,
        val detailContainer: String? = null,
        val ratingFallback: String? = null,
        val playlistType: PlaylistType = PlaylistType.IFRAME_JSON,
        val seriesUrlPatterns: List<String> = listOf("-sezon", "series/", "serialy/"),
        val knownIframeHosts: List<String> = emptyList()
    )
}

val UakinoProfile = DleProviderProfile(
    name = "Uakino",
    baseUrl = "https://uakino.best/",
    brandColor = "#ca563f",
    selectors = DleProviderProfile.Selectors(
        item = ".movie-item, .short-item, .shortstory",
        title = ".movie-title, .short-title, .shortstory-title",
        poster = "img[data-src], img[src]",
        detailContainer = ".content, #content",
        playlistType = PlaylistType.AJAX_POST,
        seriesUrlPatterns = listOf("-sezon", "seriesss/", "serialy/", "anime/", "tv-shows/"),
        knownIframeHosts = listOf("ashdi", "vidmoly", "mcloud")
    ),
    categoryPaths = mapOf(
        ContentCategory.TRENDS to "find/year/2026/f/sort=rating;desc/",
        ContentCategory.MOVIES to "filmy/online/",
        ContentCategory.SERIES to "seriesss/online/",
        ContentCategory.ANIME to "animeukr/online/",
        ContentCategory.CARTOONS to "cartoon/online/",
        ContentCategory.CARTOON_SERIES to "cartoon/cartoonseries/"
    ),
    metadataRules = mapOf(
        "genres" to listOf(ExtractionRule(".fi-item, .fi-item-s", listOf("Жанр", "Категорія"))),
        "country" to listOf(ExtractionRule(".fi-item, .fi-item-s", listOf("Країна"))),
        "actors" to listOf(ExtractionRule(".fi-item, .fi-item-s", listOf("Актори", "В ролях"))),
        "director" to listOf(ExtractionRule(".fi-item, .fi-item-s", listOf("Режисер"))),
        "duration" to listOf(ExtractionRule(".fi-item, .fi-item-s", listOf("Тривалість"))),
        "rating" to listOf(
            ExtractionRule(".fi-item, .fi-item-s", listOf("imdb"), priority = 10),
            ExtractionRule(".fi-item, .fi-item-s", listOf("рейтинг"))
        ),
        "seasonCount" to listOf(
            ExtractionRule(".story-links a[href*='sezon']"),
            ExtractionRule(".season-list a[href*='season']"),
            ExtractionRule(".playlists-lists li"),
            ExtractionRule(".block-seo-film h2")
        )
    ),
    nonGenreLabels = setOf("серіал", "мультфільм", "аніме", "мультсеріал", "аніме-серіал")
)

val EneyidaProfile = DleProviderProfile(
    name = "Eneyida",
    baseUrl = "https://eneyida.tv/",
    brandColor = "#31C469",
    selectors = DleProviderProfile.Selectors(
        item = "article.short",
        title = "a.short_title",
        poster = "a.short_img img",
        detailPoster = ".full_content-poster img",
        detailContainer = ".full_content, #content",
        ratingFallback = ".r_imdb span",
        seriesUrlPatterns = listOf("-sezon", "series/", "serialy/", "anime/", "filmi-seriali/", "anime-ukr/")
    ),
    categoryPaths = mapOf(
        ContentCategory.TRENDS to "f/sort=rating/order=desc/",
        ContentCategory.MOVIES to "films/",
        ContentCategory.SERIES to "series/",
        ContentCategory.ANIME to "anime/",
        ContentCategory.CARTOONS to "cartoon/",
        ContentCategory.CARTOON_SERIES to "cartoon-series/"
    ),
    metadataRules = mapOf(
        "genres" to listOf(ExtractionRule("li", listOf("Жанр"))),
        "country" to listOf(ExtractionRule("li", listOf("Країна"))),
        "actors" to listOf(ExtractionRule("li", listOf("Актори", "В ролях"))),
        "director" to listOf(ExtractionRule("li", listOf("Режисер"))),
        "duration" to listOf(ExtractionRule("li", listOf("Тривалість"))),
        "rating" to listOf(ExtractionRule(".r_imdb span", priority = 10)),
        "seasonCount" to listOf(ExtractionRule("li:contains(сезон)"))
    ),
    nonGenreLabels = setOf("серіал", "мультфільм", "аніме", "мультсеріал", "аніме-серіал")
)
