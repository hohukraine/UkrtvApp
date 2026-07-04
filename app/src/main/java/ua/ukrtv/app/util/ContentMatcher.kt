package ua.ukrtv.app.util

import ua.ukrtv.app.data.providers.SearchItem
import ua.ukrtv.app.data.providers.ContentUtils
import ua.ukrtv.app.domain.model.MovieDetail

object ContentMatcher {

    fun findBestMatch(detail: MovieDetail, searchResults: List<SearchItem>): SearchItem? {
        if (searchResults.isEmpty()) return null

        // 1. Priority: Exact title match AND year
        var match = searchResults.firstOrNull { s ->
            val yearsMatch = s.year != null && detail.year != null && s.year.take(4) == detail.year.take(4)
            val titleMatch = ContentUtils.isTitleMatch(s.title, detail.title, strict = true)
            yearsMatch && titleMatch
        }
        
        // 2. Title and year match (non-strict title)
        if (match == null) {
            match = searchResults.firstOrNull { s ->
                val yearsMatch = s.year != null && detail.year != null && s.year.take(4) == detail.year.take(4)
                val titleMatch = ContentUtils.isTitleMatch(s.title, detail.title, strict = false)
                yearsMatch && titleMatch
            }
        }

        // 3. Title only (if year unknown)
        if (match == null) {
            match = searchResults.firstOrNull { s ->
                val titleMatch = ContentUtils.isTitleMatch(s.title, detail.title, strict = true)
                titleMatch && detail.year == null
            }
        }

        return match
    }
}
