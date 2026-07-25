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

    // --- Type-safe route tests ---

    @Test
    fun `Search route with default query`() {
        val route = Screen.Search()
        assertEquals(Screen.Search(q = null), route)
    }

    @Test
    fun `Search route with query`() {
        val route = Screen.Search(q = "avatar")
        assertEquals("avatar", route.q)
    }

    @Test
    fun `Detail route encodes id and url`() {
        val route = Screen.Detail(id = "123", url = "https://example.com/movie.html")
        assertEquals("123", route.id)
        assertEquals("https://example.com/movie.html", route.url)
        assertNull(route.alternate)
    }

    @Test
    fun `Detail route with alternate url`() {
        val route = Screen.Detail(id = "123", url = "https://example.com/movie.html", alternate = "https://alt.com/movie.html")
        assertNotNull(route.alternate)
        assertEquals("https://alt.com/movie.html", route.alternate)
    }

    @Test
    fun `Detail route without alternate url`() {
        val route = Screen.Detail(id = "123", url = "https://example.com/movie.html")
        assertNull(route.alternate)
    }

    @Test
    fun `Player route with basic params`() {
        val route = Screen.Player(id = "123", title = "Movie Title", url = "https://example.com/stream.m3u8")
        assertEquals("123", route.id)
        assertEquals("Movie Title", route.title)
        assertEquals("https://example.com/stream.m3u8", route.url)
        assertNull(route.season)
        assertNull(route.episode)
        assertEquals("", route.poster)
    }

    @Test
    fun `Player route with season and episode`() {
        val route = Screen.Player(id = "123", title = "Movie", url = "https://example.com", season = 2, episode = 3)
        assertEquals(2, route.season)
        assertEquals(3, route.episode)
    }

    @Test
    fun `Player route without season and episode`() {
        val route = Screen.Player(id = "123", title = "Movie", url = "https://example.com")
        assertNull(route.season)
        assertNull(route.episode)
    }

    @Test
    fun `Player route with poster`() {
        val route = Screen.Player(id = "123", title = "Movie", url = "https://example.com", poster = "https://poster.jpg")
        assertEquals("https://poster.jpg", route.poster)
    }

    @Test
    fun `Player route empty poster defaults to empty`() {
        val route = Screen.Player(id = "123", title = "Movie", url = "https://example.com")
        assertEquals("", route.poster)
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
