package ua.ukrtv.app.navigation

import android.net.Uri
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppNavigationTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.encode(any<String>(), any()) } answers { firstArg<String>() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `searchRoute with empty query`() {
        assertEquals("search?q=", AppNavigation.searchRoute())
    }

    @Test
    fun `searchRoute with simple query`() {
        assertEquals("search?q=avatar", AppNavigation.searchRoute("avatar"))
    }

    @Test
    fun `searchRoute with spaces`() {
        assertEquals("search?q=the godfather", AppNavigation.searchRoute("the godfather"))
    }

    @Test
    fun `searchRoute with special characters`() {
        val result = AppNavigation.searchRoute("movie (2024)")
        assertTrue(result.startsWith("search?q="))
        assertTrue(result.contains("2024"))
    }

    @Test
    fun `detailRoute encodes id and url`() {
        val result = AppNavigation.detailRoute("123", "https://example.com/movie.html")
        assertTrue(result.startsWith("detail/123?url="))
        assertTrue(result.contains("https://example.com/movie.html"))
    }

    @Test
    fun `detailRoute with alternate url`() {
        val result = AppNavigation.detailRoute("123", "https://example.com/movie.html", "https://alt.com/movie.html")
        assertTrue(result.contains("alternate="))
        assertTrue(result.contains("https://alt.com/movie.html"))
    }

    @Test
    fun `detailRoute without alternate url`() {
        val result = AppNavigation.detailRoute("123", "https://example.com/movie.html")
        assertFalse(result.contains("alternate"))
    }

    @Test
    fun `playerRoute with basic params`() {
        val result = AppNavigation.playerRoute("123", "Movie Title", "https://example.com/stream.m3u8")
        assertTrue(result.startsWith("player/123/"))
        assertTrue(result.contains("Movie Title"))
        assertTrue(result.contains("url="))
    }

    @Test
    fun `playerRoute with season and episode`() {
        val result = AppNavigation.playerRoute("123", "Movie", "https://example.com", season = 2, episode = 3)
        assertTrue(result.contains("season=2"))
        assertTrue(result.contains("episode=3"))
    }

    @Test
    fun `playerRoute without season and episode`() {
        val result = AppNavigation.playerRoute("123", "Movie", "https://example.com")
        assertFalse(result.contains("season="))
        assertFalse(result.contains("episode="))
    }

    @Test
    fun `playerRoute with poster`() {
        val result = AppNavigation.playerRoute("123", "Movie", "https://example.com", poster = "https://poster.jpg")
        assertTrue(result.contains("poster="))
        assertTrue(result.contains("https://poster.jpg"))
    }

    @Test
    fun `playerRoute empty poster defaults to empty`() {
        val result = AppNavigation.playerRoute("123", "Movie", "https://example.com")
        assertTrue(result.contains("poster="))
    }

    // --- Route template constants ---

    @Test
    fun `HOME constant is home`() {
        assertEquals("home", AppNavigation.HOME)
    }

    @Test
    fun `SETTINGS constant is settings`() {
        assertEquals("settings", AppNavigation.SETTINGS)
    }

    @Test
    fun `SEARCH template contains q param`() {
        assertTrue(AppNavigation.SEARCH.contains("{q}"))
    }

    @Test
    fun `DETAIL template contains id and url`() {
        assertTrue(AppNavigation.DETAIL.contains("{id}"))
        assertTrue(AppNavigation.DETAIL.contains("{url}"))
    }

    @Test
    fun `PLAYER template contains all params`() {
        assertTrue(AppNavigation.PLAYER.contains("{id}"))
        assertTrue(AppNavigation.PLAYER.contains("{title}"))
        assertTrue(AppNavigation.PLAYER.contains("{url}"))
    }
}
