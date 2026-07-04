package ua.ukrtv.app.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ua.ukrtv.app.domain.model.Movie

object FastHomeParser {

    private const val MAX_HOME_ITEMS_PER_CATEGORY = 25

    suspend fun parseListOptimized(html: String, baseUrl: String, selectors: DleProviderProfile.Selectors? = null): List<Movie> = coroutineScope {
        val jsoupDeferred = async(Dispatchers.Default) {
            if (selectors != null) DleParser.parseListFastJsoupStatic(html, baseUrl, selectors)
            else emptyList()
        }
        val regexDeferred = async(Dispatchers.Default) {
            DleParser.parseListFastRegex(html, baseUrl)
        }

        val jsoupResult = jsoupDeferred.await()
        if (jsoupResult.isNotEmpty()) {
            jsoupResult.distinctBy { it.pageUrl }.take(MAX_HOME_ITEMS_PER_CATEGORY * 2)
        } else {
            regexDeferred.await().distinctBy { it.pageUrl }.take(MAX_HOME_ITEMS_PER_CATEGORY * 2)
        }
    }
}
