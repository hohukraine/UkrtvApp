package ua.ukrtv.app.providers

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.jsoup.Jsoup
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.data.providers.BaseStreamProvider
import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.network.RequestHeaders
import ua.ukrtv.app.data.network.RetryPolicy

class ParserPostTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var provider: TestProvider
    private val client = OkHttpClient()
    private val htmlHttpClient = HtmlHttpClient(client, RequestHeaders(), RetryPolicy(), null, null)

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        provider = TestProvider(client, htmlHttpClient)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `postHtml sends POST form request with browser headers`() = runBlocking {
        val url = mockWebServer.url("/index.php").toString()
        val body = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("story", "Легенда")
            .build()

        mockWebServer.enqueue(MockResponse().setBody("ok"))

        val result = provider.post(url, body, "https://example.test/")

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")?.contains("application/x-www-form-urlencoded") == true)
        assertEquals("https://example.test/", request.getHeader("Referer"))
        assertEquals("https://example.test", request.getHeader("Origin"))
        assertEquals("XMLHttpRequest", request.getHeader("X-Requested-With"))
        assertTrue(request.body.readUtf8().contains("story=%D0%9B%D0%B5%D0%B3%D0%B5%D0%BD%D0%B4%D0%B0"))
        assertEquals("ok", result)
    }

    @Test
    fun `parse Uakino search card`() {
        val html = """
            <div class="movie-item short-item">
                <div class="movie-img">
                    <img src="/uploads/mini/mainpics/poster.webp" alt="Постер серіалу">
                </div>
                <a class="movie-title" href="https://uakino.best/seriesss/comedy_series/34058-test-1-sezon.html">Легенда / Test</a>
                <div class="movie-desc">
                    <div class="movie-desk-item clearfix"><div class="fi-label">Рік виходу:</div><div class="deck-value">2026</div></div>
                    <div class="movie-desk-item clearfix"><div class="fi-label">Жанр:</div><div class="deck-value">Комедії, Драма</div></div>
                    <div class="movie-text"><span class="desc-about-text">Короткий опис серіалу</span></div>
                </div>
            </div>
        """.trimIndent()

        val movie = provider.parseUakino(html)

        assertNotNull(movie)
        assertEquals("Легенда", movie!!.title)
        assertEquals(ContentType.SERIES, movie.type)
        assertEquals("https://uakino.best/seriesss/comedy_series/34058-test-1-sezon.html", movie.pageUrl)
        assertEquals("https://example.test/uploads/mini/mainpics/poster.webp", movie.poster)
        assertEquals("Постер серіалу", movie.posterAlt)
        assertEquals("2026", movie.year)
        assertEquals(listOf("Комедії", "Драма"), movie.genres)
        assertEquals("Короткий опис серіалу", movie.shortDescription)
    }

    @Test
    fun `parse Eneyida search card`() {
        val html = """
            <article class="short related_item">
                <div class="short_in">
                    <a class="short_img img_box" href="https://eneyida.tv/9865-karate-kid-legendy.html">
                        <img data-src="/uploads/posts/2025/poster.webp" alt="Постер до Карате Кід: Легенди">
                    </a>
                    <a class="short_title" href="https://eneyida.tv/9865-karate-kid-legendy.html">Карате Кід: Легенди</a>
                    <div class="short_subtitle"><a href="https://eneyida.tv/xfsearch/year/2025/">2025</a> &bull; Action, Family</div>
                </div>
            </article>
        """.trimIndent()

        val movie = provider.parseEneyida(html)

        assertNotNull(movie)
        assertEquals("Карате Кід: Легенди", movie!!.title)
        assertEquals(ContentType.MOVIE, movie.type)
        assertEquals("https://eneyida.tv/9865-karate-kid-legendy.html", movie.pageUrl)
        assertEquals("https://example.test/uploads/posts/2025/poster.webp", movie.poster)
        assertEquals("Постер до Карате Кід: Легенди", movie.posterAlt)
        assertEquals("2025", movie.year)
        assertEquals(listOf("Action", "Family"), movie.genres)
    }

    @Test
    fun `getMovieDetails extracts metadata`() = runBlocking {
        val pageUrl = mockWebServer.url("/detail.html").toString()
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://cdn.example.test/poster.webp">
                <meta property="og:description" content="Full description">
                <meta itemprop="genre" content="Drama, Comedy">
              </head>
              <body>
                <div itemscope itemtype="http://schema.org/TVSeries">
                  <h1><span itemprop="name">Test Series</span></h1>
                  <span class="origintitle">Original Title</span>
                  <meta itemprop="dateCreated" content="2026-06-11">
                  <meta itemprop="duration" content="30 хв">
                  <div class="film-poster"><img itemprop="image" src="/poster.webp" alt="Poster"></div>
                  <div class="full-text" itemprop="description">Full description</div>
                  <div class="fi-item-s clearfix"><div class="fi-label">Жанр:</div><div class="fi-desc"><a href="/genre/drama/">Drama</a>, <a href="/genre/comedy/">Comedy</a></div></div>
                  <div class="fi-item-s clearfix"><div class="fi-label">Країна:</div><div class="fi-desc">Ukraine, Canada</div></div>
                  <div class="fi-item-s clearfix"><div class="fi-label">Режисер:</div><div class="fi-desc">Director One, Director Two</div></div>
                  <div class="fi-item-s clearfix"><div class="fi-label">Актори:</div><div class="fi-desc">Actor One, Actor Two</div></div>
                </div>
              </body>
            </html>
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(html))

        val detail = provider.getMovieDetails("1", pageUrl)

        assertEquals("Test Series", detail.title)
        assertEquals("https://cdn.example.test/poster.webp", detail.poster)
        assertEquals("Poster", detail.posterAlt)
        assertEquals("Full description", detail.description)
        assertEquals("2026-06-11", detail.year)
        assertEquals(listOf("Drama", "Comedy"), detail.genres)
        assertEquals(listOf("Ukraine", "Canada"), detail.country)
        assertEquals(listOf("Director One", "Director Two"), detail.director)
        assertEquals(listOf("Actor One", "Actor Two"), detail.actors)
        assertEquals(ContentType.SERIES, detail.contentType)
    }

    private class TestProvider(
        client: OkHttpClient,
        htmlHttpClient: HtmlHttpClient
    ) : BaseStreamProvider(client, htmlHttpClient) {
        override val name = "Test"
        override val baseUrl = "https://example.test/"
        override val hasPublicSearch = false

        override suspend fun initializeSession(): Boolean = true
        override suspend fun search(query: String, limit: Int): List<ua.ukrtv.app.data.providers.SearchItem> = emptyList()
        override suspend fun getMediaSource(pageUrl: String): MediaSource? = null
        override suspend fun getMovieDetails(id: String, url: String) = super.getMovieDetails(id, url)
        override fun supportsUrl(url: String): Boolean = true
        override fun clearCache(url: String?) = super.clearCache(url)

        suspend fun post(url: String, body: okhttp3.RequestBody, referer: String?): String? =
            postHtml(url, body, referer, isAjax = true)

        fun parseUakino(html: String) = parseMovieElement(
            Jsoup.parse(html).selectFirst(".movie-item")!!,
            "a.movie-title, .short-title a, .shortstorytitle a, .th-title a",
            ".movie-img img, .shortstoryimg img, .th-img img, img"
        )

        fun parseEneyida(html: String) = parseMovieElement(
            Jsoup.parse(html).selectFirst(".short")!!,
            ".short_title, .short-title, a.short-title, .th-title, .shortstorytitle a, .movie-title a, .short-t a, h2 a",
            ".short_img img, .movie-img img, .th-img img, img"
        )
    }
}
