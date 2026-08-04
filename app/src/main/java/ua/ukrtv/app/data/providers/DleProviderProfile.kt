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

val UaflixProfile = DleProviderProfile(
    name = "UAFLIX",
    baseUrl = "https://uafix.net/",
    brandColor = "#FF6600",
    selectors = DleProviderProfile.Selectors(
        item = ".video-item",
        title = "a.vi-img",
        poster = "img.lazy",
        cardTitle = ".vi-title",
        cardPoster = "img[src]",
        detailContainer = "article.full",
        ratingFallback = ".rat-imdb",
        seriesUrlPatterns = listOf("/serials/", "/anime/", "/dorama/", "/cartoons/", "season-", "-episode-")
    ),
    categoryPaths = mapOf(
        ContentCategory.TRENDS to "",
        ContentCategory.MOVIES to "film/",
        ContentCategory.SERIES to "serials/",
        ContentCategory.ANIME to "anime/",
        ContentCategory.CARTOONS to "cartoons/",
        ContentCategory.CARTOON_SERIES to "cartoons/"
    ),
    metadataRules = mapOf(
        "genres" to listOf(ExtractionRule("span[itemprop='genre']")),
        "country" to listOf(ExtractionRule("span.country")),
        "actors" to listOf(ExtractionRule("span[itemprop='actor']")),
        "director" to listOf(ExtractionRule("span[itemprop='director']")),
        "duration" to listOf(ExtractionRule("li.vis", regex = Regex("""/\s*(\d+\s*хв)"""))),
        "rating" to listOf(ExtractionRule(".rat-imdb", priority = 10)),
        "seasonCount" to listOf(ExtractionRule(".sect-link:contains(Сезон)"))
    ),
    nonGenreLabels = setOf("серіал", "мультфільм", "аніме", "мультсеріал", "аніме-серіал")
)
