package ua.ukrtv.app.data.streaming.strategies

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.streaming.ResolutionContext
import ua.ukrtv.app.data.streaming.getStreamType
import ua.ukrtv.app.data.streaming.inferReferer

class DirectLinkStrategyTest {

    private lateinit var strategy: DirectLinkStrategy

    @Before
    fun setUp() {
        strategy = DirectLinkStrategy()
    }

    @Test
    fun `canHandle m3u8`() = runTest {
        assertTrue(strategy.canHandle("https://cdn.example.com/stream.m3u8", ResolutionContext()))
    }

    @Test
    fun `canHandle mpd`() = runTest {
        assertTrue(strategy.canHandle("https://cdn.example.com/stream.mpd", ResolutionContext()))
    }

    @Test
    fun `canHandle mp4`() = runTest {
        assertTrue(strategy.canHandle("https://cdn.example.com/stream.mp4", ResolutionContext()))
    }

    @Test
    fun `canHandle vod URL returns false`() = runTest {
        assertFalse(strategy.canHandle("https://example.com/vod/12345", ResolutionContext()))
    }

    @Test
    fun `canHandle HTML page returns false`() = runTest {
        assertFalse(strategy.canHandle("https://example.com/page.html", ResolutionContext()))
    }

    @Test
    fun `canHandle YouTube returns false`() = runTest {
        assertFalse(strategy.canHandle("https://youtube.com/watch?v=abc", ResolutionContext()))
    }

    @Test
    fun `resolve returns result with correct url`() = runTest {
        val result = strategy.resolve("https://cdn.example.com/stream.m3u8", ResolutionContext())
        assertNotNull(result)
        assertEquals("https://cdn.example.com/stream.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve infers referer`() = runTest {
        val result = strategy.resolve("https://s11.hdvbua.pro/media/stream.m3u8", ResolutionContext())
        assertNotNull(result)
        assertEquals("https://eneyida.tv/", result!!.referer)
    }

    @Test
    fun `resolve uses context referer when provided`() = runTest {
        val ctx = ResolutionContext(referer = "https://custom.referer.com")
        val result = strategy.resolve("https://cdn.example.com/stream.m3u8", ctx)
        assertNotNull(result)
        assertEquals("https://custom.referer.com", result!!.referer)
    }

    @Test
    fun `resolve detects stream type`() = runTest {
        val result = strategy.resolve("https://cdn.example.com/stream.mpd", ResolutionContext())
        assertNotNull(result)
        assertEquals("https://cdn.example.com/stream.mpd", result!!.streamUrl)
        assertEquals(getStreamType("https://cdn.example.com/stream.mpd"), result.streamType)
    }

    @Test
    fun `strategy name is DirectLink`() {
        assertEquals("DirectLink", strategy.name)
    }
}
