package ua.ukrtv.app.data.streaming.strategies

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.providers.ProviderEpisode
import ua.ukrtv.app.data.providers.ProviderSeason
import ua.ukrtv.app.data.providers.StreamManager
import ua.ukrtv.app.data.streaming.ResolutionContext
import ua.ukrtv.app.domain.model.StreamType

class ProviderResolutionStrategyTest {

    private lateinit var streamManager: StreamManager
    private lateinit var strategy: ProviderResolutionStrategy

    @Before
    fun setUp() {
        streamManager = mockk(relaxed = true)
        strategy = ProviderResolutionStrategy(streamManager)
    }

    @Test
    fun `canHandle uakino URL`() = runTest {
        assertTrue(strategy.canHandle("https://uakino.best/movie.html", ResolutionContext()))
    }

    @Test
    fun `canHandle eneyida URL`() = runTest {
        assertTrue(strategy.canHandle("https://eneyida.tv/movie.html", ResolutionContext()))
    }

    @Test
    fun `canHandle generic URL returns false`() = runTest {
        assertFalse(strategy.canHandle("https://example.com/movie.html", ResolutionContext()))
    }

    @Test
    fun `canHandle cdn URL returns false`() = runTest {
        assertFalse(strategy.canHandle("https://cdn.example.com/stream.m3u8", ResolutionContext()))
    }

    @Test
    fun `resolve returns null when streamManager returns null`() = runTest {
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns null

        val result = strategy.resolve("https://uakino.best/movie.html", ResolutionContext())

        assertNull(result)
    }

    @Test
    fun `resolve returns null when primaryUrl is null`() = runTest {
        val source = MediaSource.Series(emptyList())
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val result = strategy.resolve("https://uakino.best/movie.html", ResolutionContext())

        assertNull(result)
    }

    @Test
    fun `resolve movie source`() = runTest {
        val source = MediaSource.Movie("https://cdn.example.com/movie.m3u8", referer = "https://uakino.best/")
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val result = strategy.resolve("https://uakino.best/movie.html", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://cdn.example.com/movie.m3u8", result!!.streamUrl)
        assertEquals(StreamType.HLS, result.streamType)
        assertEquals("https://uakino.best/", result.referer)
    }

    @Test
    fun `resolve series source with season and episode`() = runTest {
        val source = MediaSource.Series(
            seasons = listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.example.com/s1e1.m3u8"),
                    ProviderEpisode(2, "Ep 2", "https://cdn.example.com/s1e2.m3u8")
                ))
            )
        )
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val ctx = ResolutionContext(season = 1, episode = 2)
        val result = strategy.resolve("https://uakino.best/series.html", ctx)

        assertNotNull(result)
        assertEquals("https://cdn.example.com/s1e2.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve series source with voiceover`() = runTest {
        val source = MediaSource.Series(
            seasons = listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.example.com/s1e1-default.m3u8", voiceover = "Default"),
                    ProviderEpisode(1, "Ep 1", "https://cdn.example.com/s1e1-ua.m3u8", voiceover = "UA"),
                    ProviderEpisode(2, "Ep 2", "https://cdn.example.com/s1e2-default.m3u8", voiceover = "Default"),
                    ProviderEpisode(2, "Ep 2", "https://cdn.example.com/s1e2-ua.m3u8", voiceover = "UA")
                ))
            )
        )
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val ctx = ResolutionContext(season = 1, episode = 1, voiceover = "UA")
        val result = strategy.resolve("https://uakino.best/series.html", ctx)

        assertNotNull(result)
        assertEquals("https://cdn.example.com/s1e1-ua.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve series falls back to first episode when season not found`() = runTest {
        val source = MediaSource.Series(
            seasons = listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.example.com/s1e1.m3u8")
                ))
            )
        )
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val ctx = ResolutionContext(season = 99)
        val result = strategy.resolve("https://uakino.best/series.html", ctx)

        // Should use primaryUrl (first episode) since season 99 not found
        assertNotNull(result)
        assertEquals("https://cdn.example.com/s1e1.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve inferReferer when source referer is empty`() = runTest {
        val source = MediaSource.Movie("https://cdn.example.com/movie.m3u8", referer = "")
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val result = strategy.resolve("https://eneyida.tv/movie.html", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://eneyida.tv/", result!!.referer)
    }

    @Test
    fun `resolve includes seasons in result`() = runTest {
        val source = MediaSource.Series(
            seasons = listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.example.com/s1e1.m3u8")
                ))
            )
        )
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val result = strategy.resolve("https://uakino.best/series.html", ResolutionContext())

        assertNotNull(result)
        assertNotNull(result!!.seasons)
        assertEquals(1, result.seasons!!.size)
        assertEquals(1, result.seasons!![0].number)
    }

    @Test
    fun `resolve movie source has no seasons`() = runTest {
        val source = MediaSource.Movie("https://cdn.example.com/movie.m3u8")
        coEvery { streamManager.getStream(any(), any(), any(), any(), any()) } returns source

        val result = strategy.resolve("https://uakino.best/movie.html", ResolutionContext())

        assertNotNull(result)
        assertNull(result!!.seasons)
    }

    @Test
    fun `strategy name is Provider`() {
        assertEquals("Provider", strategy.name)
    }
}
