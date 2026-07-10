package ua.ukrtv.app.data.streaming

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.data.providers.MediaSource as ProviderMediaSource
import ua.ukrtv.app.data.providers.ProviderEpisode
import ua.ukrtv.app.data.providers.ProviderSeason
import ua.ukrtv.app.data.providers.MediaProvider
import ua.ukrtv.app.data.providers.SearchItem
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.streaming.strategies.*
import ua.ukrtv.app.util.AppLogger

class StreamResolverTest {

    private lateinit var fakeProvider: FakeProvider
    private lateinit var resolver: StreamResolver

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkObject(AppLogger)
        every { AppLogger.d(any<String>(), any<String>()) } just Runs
        every { AppLogger.i(any<String>(), any<String>()) } just Runs
        every { AppLogger.w(any<String>(), any<String>(), any<Throwable>()) } just Runs
        every { AppLogger.e(any<String>(), any<String>(), any<Throwable>()) } just Runs

        fakeProvider = FakeProvider()
        val providerManager = mockk<ProviderManager>(relaxed = true)
        val htmlHttpClient = mockk<ua.ukrtv.app.data.network.HtmlHttpClient>(relaxed = true)
        val resolutionLogger = mockk<ua.ukrtv.app.util.ResolutionLogger>(relaxed = true)
        val hlsExtractor = mockk<ua.ukrtv.app.data.streaming.HlsExtractor>(relaxed = true)
        
        val streamManager = FakeStreamManager(fakeProvider, providerManager)
        
        val directLinkStrategy = DirectLinkStrategy()
        val providerStrategy = ProviderResolutionStrategy(streamManager)
        val iframeStrategy = IframeResolutionStrategy(htmlHttpClient, hlsExtractor, resolutionLogger)
        
        resolver = StreamResolver(
            streamManager,
            resolutionLogger,
            directLinkStrategy,
            providerStrategy,
            iframeStrategy
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `returns only hls urls for direct m3u8`() = runBlocking {
        val url = "https://cdn.example.test/stream.m3u8"
        val result = resolver.resolve(url)
        assertNotNull(result)
        assertEquals(StreamType.HLS, result!!.streamType)
        assertEquals(url, result.streamUrl)
    }

    @Test
    fun `returns only mpd urls for direct mpd`() = runBlocking {
        val url = "https://cdn.example.test/stream.mpd"
        val result = resolver.resolve(url)
        assertNotNull(result)
        assertEquals(StreamType.MPD, result!!.streamType)
        assertEquals(url, result.streamUrl)
    }

    @Test
    fun `skips youtube links`() = runBlocking {
        val url = "https://youtube.com/watch?v=123"
        val result = resolver.resolve(url)
        assertNull(result)
    }

    @Test
    fun `deduplicates fallback streams`() = runBlocking {
        fakeProvider.stubSeries = ProviderMediaSource.Series(
            listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.test/a.m3u8"),
                    ProviderEpisode(2, "Ep 2", "https://cdn.test/b.m3u8"),
                    ProviderEpisode(1, "Ep 1 dup", "https://cdn.test/a.m3u8")
                ))
            ),
            "https://referer.test/",
            "Test"
        )
        val result = resolver.resolve("https://uakino.best/series-page.html", season = 1, episode = 1)
        assertNotNull(result)
        val fallbacks = result!!.fallbackStreams
        assertEquals(fallbacks.distinct(), fallbacks)
        assertTrue(fallbacks.size <= 8)
    }

    @Test
    fun `keeps referer from source`() = runBlocking {
        fakeProvider.stubMovie = ProviderMediaSource.Movie(
            "https://cdn.test/stream.m3u8",
            emptyList(),
            "https://uakino.best/some-page.html",
            "Uakino"
        )
        val result = resolver.resolve("https://uakino.best/some-page.html")
        assertNotNull(result)
        assertEquals("https://uakino.best/some-page.html", result!!.referer)
    }

    private class FakeStreamManager(
        private val provider: FakeProvider,
        providerManager: ProviderManager
    ) : ua.ukrtv.app.data.providers.StreamManager(providerManager) {
        override suspend fun getStream(pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean, prefetchedHtml: String?): ProviderMediaSource? {
            return provider.getMediaSource(pageUrl, season, episode, isDeep, prefetchedHtml)
        }

        override suspend fun tryProviders(pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean, prefetchedHtml: String?): ProviderMediaSource? {
            return provider.getMediaSource(pageUrl, season, episode, isDeep, prefetchedHtml)
        }
    }

    private class FakeProvider : MediaProvider {
        var stubMovie: ProviderMediaSource.Movie? = null
        var stubSeries: ProviderMediaSource.Series? = null

        override val name = "Fake"
        override val baseUrl = "https://fake.test/"
        override val brandColor = "#000000"

        override fun getHomeCategories(): List<ua.ukrtv.app.data.providers.ContentCategory> = emptyList()
        override suspend fun initializeSession(): Boolean = true
        override suspend fun getHomeSections(page: Int): List<ua.ukrtv.app.domain.model.HomeSection> = emptyList()
        override suspend fun getMoviesByCategory(category: ua.ukrtv.app.data.providers.ContentCategory, page: Int): List<ua.ukrtv.app.domain.model.Movie> = emptyList()
        override suspend fun search(query: String, limit: Int): List<SearchItem> = emptyList()
        override suspend fun getMovieDetails(url: String): MovieDetail = MovieDetail(url.hashCode().toString(), "", "", "", null, emptyList(), url, name, null, null)
        override suspend fun getMediaSource(pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean, prefetchedHtml: String?): ProviderMediaSource? {
            return if (pageUrl.contains("series")) stubSeries else stubMovie
        }
        override fun supportsUrl(url: String): Boolean = true
        override fun clearCache(url: String?) {}
    }
}
