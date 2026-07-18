package ua.ukrtv.app.data.streaming.strategies

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.streaming.HlsExtractor
import ua.ukrtv.app.data.streaming.ResolutionContext
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.ResolutionLogger

class IframeResolutionStrategyTest {

    private lateinit var htmlHttpClient: HtmlHttpClient
    private lateinit var hlsExtractor: HlsExtractor
    private lateinit var logger: ResolutionLogger
    private lateinit var strategy: IframeResolutionStrategy

    @Before
    fun setUp() {
        htmlHttpClient = mockk(relaxed = true)
        hlsExtractor = mockk(relaxed = true)
        logger = ResolutionLogger()
        strategy = IframeResolutionStrategy(htmlHttpClient, hlsExtractor, logger)
    }

    @Test
    fun `canHandle ashdi URL`() = runTest {
        assertTrue(strategy.canHandle("https://ashdi.vip/embed/123", ResolutionContext()))
    }

    @Test
    fun `canHandle hdvb URL`() = runTest {
        assertTrue(strategy.canHandle("https://hdvb.tv/embed/123", ResolutionContext()))
    }

    @Test
    fun `canHandle vidmoly URL`() = runTest {
        assertTrue(strategy.canHandle("https://vidmoly.to/embed/123", ResolutionContext()))
    }

    @Test
    fun `canHandle mcloud URL`() = runTest {
        assertTrue(strategy.canHandle("https://mcloud.to/embed/123", ResolutionContext()))
    }

    @Test
    fun `canHandle generic non-direct URL`() = runTest {
        assertTrue(strategy.canHandle("https://some-random-site.com/player", ResolutionContext()))
    }

    @Test
    fun `canHandle direct stream URL returns false`() = runTest {
        assertFalse(strategy.canHandle("https://cdn.example.com/stream.m3u8", ResolutionContext()))
    }

    @Test
    fun `canHandle uakino URL returns false`() = runTest {
        assertFalse(strategy.canHandle("https://uakino.best/movie.html", ResolutionContext()))
    }

    @Test
    fun `canHandle eneyida URL returns false`() = runTest {
        assertFalse(strategy.canHandle("https://eneyida.tv/movie.html", ResolutionContext()))
    }

    @Test
    fun `resolve extracts links from HTML`() = runTest {
        coEvery { htmlHttpClient.getHtml(any(), any()) } returns "<html>player data</html>"
        coEvery { hlsExtractor.extractFromHtml(any()) } returns listOf(
            HlsExtractor.ExtractResult("https://cdn.example.com/master.m3u8", StreamType.HLS)
        )

        val result = strategy.resolve("https://ashdi.vip/embed/123", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://cdn.example.com/master.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve returns null on HTML fetch failure`() = runTest {
        coEvery { htmlHttpClient.getHtml(any(), any()) } throws RuntimeException("Network error")

        val result = strategy.resolve("https://ashdi.vip/embed/123", ResolutionContext())

        assertNull(result)
    }

    @Test
    fun `resolve returns null when no links extracted`() = runTest {
        coEvery { htmlHttpClient.getHtml(any(), any()) } returns "<html>empty</html>"
        coEvery { hlsExtractor.extractFromHtml(any()) } returns emptyList()

        val result = strategy.resolve("https://ashdi.vip/embed/123", ResolutionContext())

        assertNull(result)
    }

    @Test
    fun `resolve prefers master playlist`() = runTest {
        coEvery { htmlHttpClient.getHtml(any(), any()) } returns "<html>data</html>"
        coEvery { hlsExtractor.extractFromHtml(any()) } returns listOf(
            HlsExtractor.ExtractResult("https://cdn.example.com/segment.m3u8", StreamType.HLS),
            HlsExtractor.ExtractResult("https://cdn.example.com/master.m3u8", StreamType.HLS)
        )

        val result = strategy.resolve("https://ashdi.vip/embed/123", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://cdn.example.com/master.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve uses context referer`() = runTest {
        coEvery { htmlHttpClient.getHtml(any(), any()) } returns "<html>data</html>"
        coEvery { hlsExtractor.extractFromHtml(any()) } returns listOf(
            HlsExtractor.ExtractResult("https://cdn.example.com/stream.m3u8", StreamType.HLS)
        )

        val ctx = ResolutionContext(referer = "https://custom.referer.com")
        strategy.resolve("https://ashdi.vip/embed/123", ctx)

        coEvery { htmlHttpClient.getHtml("https://ashdi.vip/embed/123", "https://custom.referer.com") }
    }

    @Test
    fun `strategy name is Iframe`() {
        assertEquals("Iframe", strategy.name)
    }
}
