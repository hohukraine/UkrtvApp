package ua.ukrtv.app.data.providers

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SeriesIndexRepository
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.util.AppLogger

class UaflixProviderTest {

    private val serialUrl = "https://uafix.net/serials/gt-diim-v-drakona/"
    private val baseUrl = "https://uafix.net/"

    private val season1Url = "https://uafix.net/serials/gt-diim-v-drakona/sezon-1/"
    private val season2Url = "https://uafix.net/serials/gt-diim-v-drakona/sezon-2/"
    private val season3Url = "https://uafix.net/serials/gt-diim-v-drakona/sezon-3/"

    private fun episodeLink(season: Int, episode: Int, extra: String = ""): String {
        val prefix = "https://uafix.net/serials/gt-diim-v-drakona/"
        return "%sseason-%02d-episode-%02d/%s".format(prefix, season, episode, extra)
    }

    // Mirrors the real Uaflix serial page: the hero widget and the "watch" button both point
    // at S1E1 (duplicate), while the grid only lists a subset of episodes (1, 6..10 for S1).
    private fun serialPageHtml(): String = """
        <html><body>
        <div class="hero">
            <a href="${episodeLink(1, 1)}">Дивитись 1 серію</a>
            <a href="${episodeLink(1, 1)}">Почати перегляд</a>
        </div>
        <div class="grid">
            <a href="${episodeLink(1, 6)}">6</a>
            <a href="${episodeLink(1, 7)}">7</a>
            <a href="${episodeLink(1, 8)}">8</a>
            <a href="${episodeLink(1, 9)}">9</a>
            <a href="${episodeLink(1, 10)}">10</a>
            <a href="${episodeLink(2, 1, "v1/")}">S2E1</a>
            <a href="${episodeLink(2, 2, "v1/")}">S2E2</a>
        </div>
        <nav>
            <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-1/">Сезон 1</a>
            <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-2/">Сезон 2</a>
            <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-3/">Сезон 3</a>
        </nav>
        </body></html>
    """.trimIndent()

    private fun seasonPageHtml(season: Int, episodeCount: Int): String {
        val eps = (1..episodeCount).joinToString("\n") { n ->
            "            <a href=\"${episodeLink(season, n)}\">${n} серія</a>"
        }
        return """
            <html><body>
            <div class="list">
            $eps
            </div>
            <nav>
                <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-1/">Сезон 1</a>
                <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-2/">Сезон 2</a>
                <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-3/">Сезон 3</a>
            </nav>
            </body></html>
        """.trimIndent()
    }

    private lateinit var htmlClient: HtmlHttpClient
    private lateinit var provider: UaflixProvider
    private lateinit var seriesIndexRepo: SeriesIndexRepository

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0

        mockkObject(AppLogger)
        every { AppLogger.d(any<String>(), any<String>()) } just Runs
        every { AppLogger.i(any<String>(), any<String>()) } just Runs
        every { AppLogger.e(any<String>(), any<String>()) } just Runs
        every { AppLogger.w(any<String>(), any<String>(), any<Throwable>()) } just Runs
        every { AppLogger.w(any<String>(), any<String>()) } just Runs

        htmlClient = mockk<HtmlHttpClient>(relaxed = true)
        val sessionRepo = mockk<SessionRepository>(relaxed = true)
        val catalogRepo = mockk<CatalogRepository>(relaxed = true)
        seriesIndexRepo = mockk<SeriesIndexRepository>(relaxed = true)
        provider = UaflixProvider(htmlClient, sessionRepo, catalogRepo, seriesIndexRepo)
        coEvery { htmlClient.getHtml(serialUrl, baseUrl) } returns serialPageHtml()
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    private suspend fun resolve(
        season: Int? = null,
        episode: Int? = null,
        isDeep: Boolean = false
    ): MediaSource.Series {
        val source = provider.getMediaSource(serialUrl, season, episode, isDeep)
        assertNotNull(source)
        return source as MediaSource.Series
    }

    @Test
    fun `duplicate hero links are deduped without fetching season pages`() = runTest {
        val source = resolve(season = 1, episode = 1)

        val s1 = source.seasons.first { it.number == 1 }
        assertEquals(listOf(1, 6, 7, 8, 9, 10), s1.episodes.map { it.number })

        val s2 = source.seasons.first { it.number == 2 }
        assertEquals(listOf(1, 2), s2.episodes.map { it.number })

        coVerify(exactly = 0) { htmlClient.getHtml(season1Url, any()) }
        coVerify(exactly = 0) { htmlClient.getHtml(season2Url, any()) }
    }

    @Test
    fun `missing target episode completes season list from sezon page`() = runTest {
        coEvery { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) } returns seasonPageHtml(1, 10)

        val source = resolve(season = 1, episode = 2)

        val s1 = source.seasons.first { it.number == 1 }
        assertEquals((1..10).toList(), s1.episodes.map { it.number })

        coVerify(exactly = 1) { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) }
    }

    @Test
    fun `deep resolution completes every season from its sezon page`() = runTest {
        coEvery { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) } returns seasonPageHtml(1, 10)
        coEvery { htmlClient.getHtml(season2Url, serialUrl, skipRateLimitRetry = true) } returns seasonPageHtml(2, 8)
        coEvery { htmlClient.getHtml(season3Url, serialUrl, skipRateLimitRetry = true) } returns seasonPageHtml(3, 7)

        val source = resolve(isDeep = true)

        assertEquals(3, source.seasons.size)
        assertEquals((1..10).toList(), source.seasons.first { it.number == 1 }.episodes.map { it.number })
        assertEquals((1..8).toList(), source.seasons.first { it.number == 2 }.episodes.map { it.number })
        assertEquals((1..7).toList(), source.seasons.first { it.number == 3 }.episodes.map { it.number })

        coVerify(exactly = 1) { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) }
        coVerify(exactly = 1) { htmlClient.getHtml(season2Url, serialUrl, skipRateLimitRetry = true) }
        coVerify(exactly = 1) { htmlClient.getHtml(season3Url, serialUrl, skipRateLimitRetry = true) }
    }

    @Test
    fun `season pages without episode links are skipped safely`() = runTest {
        coEvery { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) } returns "<html><body>no links</body></html>"

        val source = resolve(season = 1, episode = 2)

        val s1 = source.seasons.firstOrNull { it.number == 1 }
        assertNotNull(s1)
        // Unchanged from the grid parse, but still deduped.
        assertEquals(listOf(1, 6, 7, 8, 9, 10), s1!!.episodes.map { it.number })
    }

    @Test
    fun `indexed serial builds full structure without season page fetches`() = runTest {
        val indexedSeasons = mapOf(
            1 to (1..10).toList(),
            2 to (1..8).toList(),
            3 to listOf(1)
        )
        every { seriesIndexRepo.uaflixEpisodes("gt-diim-v-drakona") } returns indexedSeasons
        every { seriesIndexRepo.uaflixVariantUrl(any(), any(), any()) } returns null

        val source = resolve(isDeep = true)

        assertEquals(3, source.seasons.size)
        assertEquals((1..10).toList(), source.seasons.first { it.number == 1 }.episodes.map { it.number })
        assertEquals((1..8).toList(), source.seasons.first { it.number == 2 }.episodes.map { it.number })
        assertEquals(listOf(1), source.seasons.first { it.number == 3 }.episodes.map { it.number })

        coVerify(exactly = 0) { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) }
        coVerify(exactly = 0) { htmlClient.getHtml(season2Url, serialUrl, skipRateLimitRetry = true) }
        coVerify(exactly = 0) { htmlClient.getHtml(season3Url, serialUrl, skipRateLimitRetry = true) }
    }

    @Test
    fun `indexed variant episode uses full variant url`() = runTest {
        val indexedSeasons = mapOf(1 to listOf(1, 2), 2 to listOf(1))
        every { seriesIndexRepo.uaflixVariantUrl(any(), any(), any()) } returns null
        every { seriesIndexRepo.uaflixEpisodes("gt-diim-v-drakona") } returns indexedSeasons
        every { seriesIndexRepo.uaflixVariantUrl("gt-diim-v-drakona", 2, 1) } returns
            "https://uafix.net/serials/gt-diim-v-drakona/season-02-episode-01/v1/"

        val source = resolve()

        val s2 = source.seasons.first { it.number == 2 }
        assertEquals("https://uafix.net/serials/gt-diim-v-drakona/season-02-episode-01/v1/", s2.episodes.first().url)
        // Non-variant episodes reconstruct the canonical URL.
        val s1 = source.seasons.first { it.number == 1 }
        assertEquals("https://uafix.net/serials/gt-diim-v-drakona/season-01-episode-01/", s1.episodes.first().url)

        coVerify(exactly = 0) { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) }
        coVerify(exactly = 0) { htmlClient.getHtml(season2Url, serialUrl, skipRateLimitRetry = true) }
    }

    @Test
    fun `unknown slug falls back to runtime parsing`() = runTest {
        every { seriesIndexRepo.uaflixEpisodes(any()) } returns null

        val source = resolve(season = 1, episode = 1)

        val s1 = source.seasons.first { it.number == 1 }
        assertEquals(listOf(1, 6, 7, 8, 9, 10), s1.episodes.map { it.number })
    }

    private fun pagedSeasonPageHtml(episodes: List<Int>, withNav: Boolean): String {
        val eps = episodes.joinToString("\n") { n ->
            "            <a href=\"${episodeLink(1, n)}\">${n} серія</a>"
        }
        val nav = if (withNav) """
            <div id="bottom-nav"><ul class="pagination">
                <li class="active"><span>1</span></li>
                <li><a href="${season1Url}?page=2">2</a></li>
            </ul></div>
        """.trimIndent() else ""
        return """
            <html><body>
            <div class="list">
            $eps
            </div>
            $nav
            </body></html>
        """.trimIndent()
    }

    @Test
    fun `paged season page completes via page 2`() = runTest {
        coEvery { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) } returns
            pagedSeasonPageHtml(episodes = (7..26).toList(), withNav = true)
        coEvery { htmlClient.getHtml("$season1Url?page=2", serialUrl, skipRateLimitRetry = true) } returns
            pagedSeasonPageHtml(episodes = (1..6).toList(), withNav = false)

        val source = resolve(season = 1, episode = 26)

        val s1 = source.seasons.first { it.number == 1 }
        assertEquals((1..26).toList(), s1.episodes.map { it.number })

        // The pagination link must not be treated as an extra season page.
        coVerify(exactly = 1) { htmlClient.getHtml(season1Url, serialUrl, skipRateLimitRetry = true) }
        coVerify(exactly = 1) { htmlClient.getHtml("$season1Url?page=2", serialUrl, skipRateLimitRetry = true) }
    }

    @Test
    fun `season url entry still resolves every season`() = runTest {
        val season1Entry = """
            <html><body>
            <div class="list">
                <a href="${episodeLink(1, 1)}">1 серія</a>
            </div>
            <nav>
                <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-1/">Сезон 1</a>
                <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-2/">Сезон 2</a>
                <a class="sect-link" href="/serials/gt-diim-v-drakona/sezon-3/">Сезон 3</a>
            </nav>
            </body></html>
        """.trimIndent()
        coEvery { htmlClient.getHtml(season1Url, baseUrl) } returns season1Entry
        coEvery { htmlClient.getHtml(season1Url, season1Url, skipRateLimitRetry = true) } returns seasonPageHtml(1, 10)
        coEvery { htmlClient.getHtml(season2Url, season1Url, skipRateLimitRetry = true) } returns seasonPageHtml(2, 8)
        coEvery { htmlClient.getHtml(season3Url, season1Url, skipRateLimitRetry = true) } returns seasonPageHtml(3, 7)

        val source = provider.getMediaSource(season1Url, null, null, isDeep = true) as MediaSource.Series

        assertEquals(3, source.seasons.size)
        assertEquals((1..10).toList(), source.seasons.first { it.number == 1 }.episodes.map { it.number })
        assertEquals((1..8).toList(), source.seasons.first { it.number == 2 }.episodes.map { it.number })
        assertEquals((1..7).toList(), source.seasons.first { it.number == 3 }.episodes.map { it.number })
    }
}
