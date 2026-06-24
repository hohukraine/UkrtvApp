package ua.ukrtv.app.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class TvSearchProvider : ContentProvider() {

    companion object {
        private const val SEARCH_SUGGESTIONS = 1
        private const val SEARCH_SUGGESTIONS_QUERY = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI("ua.ukrtv.app.tvsearch",
                SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGESTIONS)
            addURI("ua.ukrtv.app.tvsearch",
                SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGESTIONS_QUERY)
        }

        private val SEARCH_COLUMNS = arrayOf(
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        when (uriMatcher.match(uri)) {
            SEARCH_SUGGESTIONS, SEARCH_SUGGESTIONS_QUERY -> {
                val query = uri.lastPathSegment?.lowercase() ?: return null
                if (query.isEmpty() || query == SearchManager.SUGGEST_URI_PATH_QUERY) {
                    return getPopularSuggestions()
                }
                return searchContent(query)
            }
            else -> return null
        }
    }

    private fun getPopularSuggestions(): Cursor {
        val cursor = MatrixCursor(SEARCH_COLUMNS)
        val popularQueries = listOf(
            "фільми українською",
            "серіали українською",
            "мультфільми",
            "аніме",
            "новинки"
        )
        popularQueries.forEachIndexed { index, query ->
            cursor.addRow(
                arrayOf(
                    query,
                    "Популярний запит",
                    "ua.ukrtv.app://search/$query",
                    android.content.Intent.ACTION_SEARCH,
                    "vnd.android.cursor.item/vnd.ukrtv.search"
                )
            )
        }
        return cursor
    }

    private fun searchContent(query: String): Cursor {
        val cursor = MatrixCursor(SEARCH_COLUMNS)

        val utfQuery = java.net.URLEncoder.encode(query, "UTF-8")
        cursor.addRow(
            arrayOf(
                "Пошук: $query",
                "Натисніть для пошуку",
                "ua.ukrtv.app://search/$utfQuery",
                android.content.Intent.ACTION_SEARCH,
                "vnd.android.cursor.item/vnd.ukrtv.search"
            )
        )

        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            SEARCH_SUGGESTIONS, SEARCH_SUGGESTIONS_QUERY ->
                SearchManager.SUGGEST_MIME_TYPE
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
