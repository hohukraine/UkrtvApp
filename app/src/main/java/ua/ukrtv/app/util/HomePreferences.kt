package ua.ukrtv.app.util

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class HomeLayout(
    val showContinueWatching: Boolean = true,
    val showWatchlist: Boolean = true,
    val showTrends: Boolean = true,
    val showMovies: Boolean = false,
    val showSeries: Boolean = false,
    val showAnime: Boolean = false,
    val showCartoons: Boolean = false,
    val showCartoonSeries: Boolean = false
)

@Singleton
class HomePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("home_prefs", Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(readLayout())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    private val _defaultProvider = MutableStateFlow(readDefaultProvider())
    val defaultProvider: StateFlow<String> = _defaultProvider.asStateFlow()

    fun getLayout(): HomeLayout = _layout.value

    fun setLayout(layout: HomeLayout) {
        prefs.edit()
            .putBoolean(KEY_CONTINUE_WATCHING, layout.showContinueWatching)
            .putBoolean(KEY_WATCHLIST, layout.showWatchlist)
            .putBoolean(KEY_TRENDS, layout.showTrends)
            .putBoolean(KEY_MOVIES, layout.showMovies)
            .putBoolean(KEY_SERIES, layout.showSeries)
            .putBoolean(KEY_ANIME, layout.showAnime)
            .putBoolean(KEY_CARTOONS, layout.showCartoons)
            .putBoolean(KEY_CARTOON_SERIES, layout.showCartoonSeries)
            .apply()
        _layout.value = layout
    }

    fun getDefaultProvider(): String = _defaultProvider.value

    fun setDefaultProvider(providerId: String) {
        prefs.edit().putString(KEY_DEFAULT_PROVIDER, providerId).apply()
        _defaultProvider.value = providerId
    }

    private fun readLayout(): HomeLayout {
        return HomeLayout(
            showContinueWatching = prefs.getBoolean(KEY_CONTINUE_WATCHING, true),
            showWatchlist = prefs.getBoolean(KEY_WATCHLIST, true),
            showTrends = prefs.getBoolean(KEY_TRENDS, true),
            showMovies = prefs.getBoolean(KEY_MOVIES, false),
            showSeries = prefs.getBoolean(KEY_SERIES, false),
            showAnime = prefs.getBoolean(KEY_ANIME, false),
            showCartoons = prefs.getBoolean(KEY_CARTOONS, false),
            showCartoonSeries = prefs.getBoolean(KEY_CARTOON_SERIES, false)
        )
    }

    private fun readDefaultProvider(): String {
        return prefs.getString(KEY_DEFAULT_PROVIDER, "Eneyida") ?: "Eneyida"
    }

    companion object {
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_CONTINUE_WATCHING = "show_continue_watching"
        private const val KEY_WATCHLIST = "show_watchlist"
        private const val KEY_TRENDS = "show_trends"
        private const val KEY_MOVIES = "show_movies"
        private const val KEY_SERIES = "show_series"
        private const val KEY_ANIME = "show_anime"
        private const val KEY_CARTOONS = "show_cartoons"
        private const val KEY_CARTOON_SERIES = "show_cartoon_series"
    }
}
