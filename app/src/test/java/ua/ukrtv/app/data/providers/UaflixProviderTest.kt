package ua.ukrtv.app.data.providers

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
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
        provider = UaflixProvider(htmlClient, sessionRepo, catalogRepo)
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
        coEvery { htmlClient.getHtml(season1Url, serialUrl) } returns seasonPageHtml(1, 10)

        val source = resolve(season = 1, episode = 2)

        val s1 = source.seasons.first { it.number == 1 }
        assertEquals((1..10).toList(), s1.episodes.map { it.number })

        coVerify(exactly = 1) { htmlClient.getHtml(season1Url, serialUrl) }
    }

    @Test
    fun `deep resolution completes every season from its sezon page`() = runTest {
        coEvery { htmlClient.getHtml(season1Url, serialUrl) } returns seasonPageHtml(1, 10)
        coEvery { htmlClient.getHtml(season2Url, serialUrl) } returns seasonPageHtml(2, 8)
        coEvery { htmlClient.getHtml(season3Url, serialUrl) } returns seasonPageHtml(3, 7)

        val source = resolve(isDeep = true)

        assertEquals(3, source.seasons.size)
        assertEquals((1..10).toList(), source.seasons.first { it.number == 1 }.episodes.map { it.number })
        assertEquals((1..8).toList(), source.seasons.first { it.number == 2 }.episodes.map { it.number })
        assertEquals((1..7).toList(), source.seasons.first { it.number == 3 }.episodes.map { it.number })

        coVerify(exactly = 1) { htmlClient.getHtml(season1Url, serialUrl) }
        coVerify(exactly = 1) { htmlClient.getHtml(season2Url, serialUrl) }
        coVerify(exactly = 1) { htmlClient.getHtml(season3Url, serialUrl) }
    }

    @Test
    fun `season pages without episode links are skipped safely`() = runTest {
        coEvery { htmlClient.getHtml(season1Url, serialUrl) } returns "<html><body>no links</body></html>"

        val source = resolve(season = 1, episode = 2)

        val s1 = source.seasons.firstOrNull { it.number == 1 }
        assertNotNull(s1)
        // Unchanged from the grid parse, but still deduped.
        assertEquals(listOf(1, 6, 7, 8, 9, 10), s1!!.episodes.map { it.number })
    }
}
