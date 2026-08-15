package ua.ukrtv.app.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable data object Home : Screen
    @Serializable data class Search(val q: String? = null) : Screen
    @Serializable data object Top200 : Screen
    @Serializable data object TrendsGrid : Screen
    @Serializable data object Watchlist : Screen
    @Serializable data class Detail(
        val id: String,
        val url: String,
        val alternate: String? = null
    ) : Screen
    @Serializable data class Player(
        val id: String,
        val title: String,
        val url: String,
        val season: Int? = null,
        val episode: Int? = null,
        val poster: String = "",
        val brandColor: String? = null
    ) : Screen
    @Serializable data class Settings(val checkForUpdate: Boolean = false) : Screen
    @Serializable data class CategoryGrid(val category: String) : Screen
}

object AppNavigation {
    // Legacy support for older callers if needed
    const val HOME = "home"
    const val SEARCH = "search?q={q}"
    const val TOP_200 = "top_200"
    const val TRENDS_GRID = "trends_grid"
    const val DETAIL = "detail/{id}?url={url}&alternate={alternate}"
    const val PLAYER = "player/{id}/{title}?url={url}&season={season}&episode={episode}&poster={poster}&brandColor={brandColor}"
    const val SETTINGS = "settings"
    const val CATEGORY_GRID = "category_grid?category={category}"
}
