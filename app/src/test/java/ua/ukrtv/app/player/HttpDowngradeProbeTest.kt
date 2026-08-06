package ua.ukrtv.app.player

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.AppLogger

class HttpDowngradeProbeTest {

    private lateinit var server: MockWebServer
    private lateinit var probe: HttpDowngradeProbe

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        mockkObject(AppLogger)
        every { AppLogger.d(any<String>(), any<String>()) } just Runs

        probe = HttpDowngradeProbe(
            okHttpClient = OkHttpClient.Builder().build(),
            problemHosts = listOf("localhost")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkAll()
    }

    private fun httpsUrl(path: String): String = "https://localhost:${server.port}/$path"

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `downgrades when http endpoint responds and HLS playlist has no absolute https urls`() = runTest {
        enqueue("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:10,\nsegment0.ts\n")

        val result = probe.maybeDowngrade(httpsUrl("index.m3u8"), StreamType.HLS, "")

        assertEquals("http://localhost:${server.port}/index.m3u8", result)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertTrue(request.requestUrl!!.isHttps.not())
        assertTrue(request.path!!.contains("index.m3u8"))
    }

    @Test
    fun `keeps https when playlist body references absolute https urls`() = runTest {
        enqueue("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nhttps://cdn.example/video.m3u8\n")

        val result = probe.maybeDowngrade(httpsUrl("index.m3u8"), StreamType.HLS, "")

        assertEquals(httpsUrl("index.m3u8"), result)
    }

    @Test
    fun `downgrades MP4 streams on http 200`() = runTest {
        enqueue("", code = 200)

        val result = probe.maybeDowngrade(httpsUrl("movie.mp4"), StreamType.MP4, "")

        assertEquals("http://localhost:${server.port}/movie.mp4", result)
    }

    @Test
    fun `keeps https when http endpoint fails`() = runTest {
        enqueue("", code = 404)

        val result = probe.maybeDowngrade(httpsUrl("index.m3u8"), StreamType.HLS, "")

        assertEquals(httpsUrl("index.m3u8"), result)
    }

    @Test
    fun `keeps https for hosts outside the problem list`() = runTest {
        val result = probe.maybeDowngrade("https://cdn.example.com/stream.m3u8", StreamType.HLS, "")

        assertEquals("https://cdn.example.com/stream.m3u8", result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `keeps http url untouched`() = runTest {
        val result = probe.maybeDowngrade("http://localhost:${server.port}/index.m3u8", StreamType.HLS, "")

        assertEquals("http://localhost:${server.port}/index.m3u8", result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `caches decision per host and does not probe again`() = runTest {
        enqueue("#EXTM3U\n")
        probe.maybeDowngrade(httpsUrl("index.m3u8"), StreamType.HLS, "")
        probe.maybeDowngrade(httpsUrl("other.m3u8"), StreamType.HLS, "")

        assertEquals(1, server.requestCount)
        assertEquals("http://localhost:${server.port}/other.m3u8", probe.maybeDowngrade(httpsUrl("other.m3u8"), StreamType.HLS, ""))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sends referer header when provided`() = runTest {
        enqueue("#EXTM3U\n")
        probe.maybeDowngrade(httpsUrl("index.m3u8"), StreamType.HLS, "https://ashdi.vip/vod/1")

        val request = server.takeRequest()
        assertEquals("https://ashdi.vip/vod/1", request.getHeader("Referer"))
    }
}
