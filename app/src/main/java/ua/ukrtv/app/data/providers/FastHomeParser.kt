package ua.ukrtv.app.data.providers

import ua.ukrtv.app.domain.model.Movie

object FastHomeParser {

    private const val MAX_HOME_ITEMS_PER_CATEGORY = 14

    fun parseListOptimized(html: String, baseUrl: String, selectors: DleProviderProfile.Selectors? = null): List<Movie> {
        val result = if (selectors != null) {
            DleParser.parseListFastJsoupStatic(html, baseUrl, selectors)
        } else {
            DleParser.parseListFastRegex(html, baseUrl)
        }
        return result.distinctBy { it.pageUrl }.take(MAX_HOME_ITEMS_PER_CATEGORY * 2)
    }
}
