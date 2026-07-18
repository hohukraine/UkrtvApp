package ua.ukrtv.app.util

import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Voiceover

class SearchScorerTest {

    @Test
    fun `stripAccents removes combining marks`() {
        val result = SearchScorer.stripAccents("Caf\u00e9")
        assertEquals("Cafe", result)
    }

    @Test
    fun `normalizeTitle lowercases and removes special chars`() {
        assertEquals("avatar", SearchScorer.normalizeTitle("Avatar"))
    }

    @Test
    fun `normalizeTitle removes year in parens`() {
        assertEquals("avatar", SearchScorer.normalizeTitle("Avatar (2009)"))
    }

    @Test
    fun `normalizeTitle removes dots and dashes`() {
        assertEquals("mr robot", SearchScorer.normalizeTitle("Mr. Robot"))
    }

    @Test
    fun `normalizeTitle collapses whitespace`() {
        assertEquals("the godfather", SearchScorer.normalizeTitle("  The   Godfather  "))
    }

    @Test
    fun `transliterate Ukrainian to Latin`() {
        val result = SearchScorer.transliterate("Чорнобиль")
        assertEquals("chornobyl", result)
    }

    @Test
    fun `transliterate simple`() {
        assertEquals("avatar", SearchScorer.transliterate("аватар"))
    }

    @Test
    fun `tokenSetSimilarity identical strings`() {
        assertEquals(1.0f, SearchScorer.tokenSetSimilarity("avatar", "avatar"), 0.01f)
    }

    @Test
    fun `tokenSetSimilarity same tokens different order`() {
        assertEquals(1.0f, SearchScorer.tokenSetSimilarity("the godfather", "godfather the"), 0.01f)
    }

    @Test
    fun `tokenSetSimilarity partial overlap`() {
        val score = SearchScorer.tokenSetSimilarity("the godfather", "the matrix")
        assertTrue(score > 0f && score < 1f)
    }

    @Test
    fun `tokenSetSimilarity no overlap`() {
        assertEquals(0f, SearchScorer.tokenSetSimilarity("avatar", "matrix"), 0.01f)
    }

    @Test
    fun `tokenSetSimilarity empty string`() {
        assertEquals(0f, SearchScorer.tokenSetSimilarity("", "avatar"), 0.01f)
    }

    @Test
    fun `bigramSimilarity identical strings`() {
        val score = SearchScorer.bigramSimilarity("avatar", "avatar")
        assertTrue(score > 0.9f)
    }

    @Test
    fun `bigramSimilarity similar strings`() {
        val score = SearchScorer.bigramSimilarity("avatar", "avatars")
        assertTrue(score > 0.7f)
    }

    @Test
    fun `bigramSimilarity different strings`() {
        val score = SearchScorer.bigramSimilarity("avatar", "matrix")
        assertTrue(score < 0.3f)
    }

    @Test
    fun `bigramSimilarity short strings`() {
        assertEquals(0f, SearchScorer.bigramSimilarity("a", "b"), 0.01f)
    }

    @Test
    fun `extractYear from title`() {
        assertEquals(2009, SearchScorer.extractYear("Avatar (2009)"))
    }

    @Test
    fun `extractYear 1900s`() {
        assertEquals(1994, SearchScorer.extractYear("The Shawshank Redemption 1994"))
    }

    @Test
    fun `extractYear no year`() {
        assertNull(SearchScorer.extractYear("Avatar"))
    }

    @Test
    fun `extractYear invalid range`() {
        assertNull(SearchScorer.extractYear("Movie 1899"))
    }

    @Test
    fun `pickBestMatch exact match`() {
        val movies = listOf(
            makeMovie("Avatar", "https://example.com/avatar.html", 2009),
            makeMovie("Avatar 2", "https://example.com/avatar2.html", 2022)
        )
        val result = SearchScorer.pickBestMatch(movies, listOf("Avatar"), 2009)
        assertNotNull(result)
        assertEquals("Avatar", result!!.title)
    }

    @Test
    fun `pickBestMatch no results returns null`() {
        assertNull(SearchScorer.pickBestMatch(emptyList(), listOf("Avatar")))
    }

    @Test
    fun `pickBestMatch empty queries returns null`() {
        val movies = listOf(makeMovie("Avatar", "https://example.com/avatar.html"))
        assertNull(SearchScorer.pickBestMatch(movies, emptyList()))
    }

    @Test
    fun `pickBestMatch prefers year match`() {
        val movies = listOf(
            makeMovie("Avatar", "https://example.com/avatar.html", 2009),
            makeMovie("Avatar", "https://example.com/avatar-2022.html", 2022)
        )
        val result = SearchScorer.pickBestMatch(movies, listOf("Avatar"), 2009)
        assertNotNull(result)
        assertEquals(2009, result!!.year)
    }

    @Test
    fun `pickBestMatch transliterated query matches Ukrainian title`() {
        val movies = listOf(
            makeMovie("Чорнобиль", "https://uakino.best/chornobyl.html")
        )
        val result = SearchScorer.pickBestMatch(movies, listOf("Чорнобиль"))
        assertNotNull(result)
    }

    @Test
    fun `pickBestMatch penalizes series URL for movie search`() {
        val movies = listOf(
            makeMovie("Avatar", "https://example.com/serials/avatar.html"),
            makeMovie("Avatar", "https://example.com/movies/avatar.html")
        )
        val result = SearchScorer.pickBestMatch(movies, listOf("Avatar"))
        assertNotNull(result)
        assertEquals("https://example.com/movies/avatar.html", result!!.pageUrl)
    }

    private fun makeMovie(title: String, url: String, year: Int? = null) = Movie(
        id = url.hashCode().toString(),
        title = title,
        poster = "",
        pageUrl = url,
        year = year
    )
}
