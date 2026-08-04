package ua.ukrtv.app.data.streaming

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HlsPlaylistDurationTest {

    private lateinit var durationResolver: HlsPlaylistDuration

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        durationResolver = HlsPlaylistDuration(mockk<OkHttpClient>(relaxed = true))
    }

    @Test
    fun `sumExtinf sums segment durations`() {
        val content = """
            #EXTM3U
            #EXTINF:5.005000,
            https://cdn/seg1.ts
            #EXTINF:4.500000,
            https://cdn/seg2.ts
            #EXTINF:3.250000,
            https://cdn/seg3.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(12755L, durationResolver.sumExtinf(content))
    }

    @Test
    fun `sumExtinf returns zero for empty playlist`() {
        assertEquals(0L, durationResolver.sumExtinf(""))
    }

    @Test
    fun `pickBestVariant selects highest bandwidth`() {
        val content = """
            #EXTM3U
            #EXT-X-STREAM-INF:RESOLUTION=854x480,BANDWIDTH=714000
            https://cdn/480/index.m3u8
            #EXT-X-STREAM-INF:RESOLUTION=1920x1080,BANDWIDTH=2128000
            https://cdn/1080/index.m3u8
            #EXT-X-STREAM-INF:RESOLUTION=1280x720,BANDWIDTH=1096000
            https://cdn/720/index.m3u8
        """.trimIndent()
        val best = durationResolver.pickBestVariant(content)
        assertEquals("https://cdn/1080/index.m3u8", best?.url)
        assertEquals(2128000L, best?.bandwidth)
    }

    @Test
    fun `pickBestVariant returns null without stream-inf`() {
        assertNull(durationResolver.pickBestVariant("#EXTM3U\n#EXTINF:5.0,\nhttps://cdn/seg.ts"))
    }

    @Test
    fun `resolveRelativeUrl resolves relative variant`() {
        assertEquals(
            "https://cdn/video/1080/index.m3u8",
            durationResolver.resolveRelativeUrl("https://cdn/video/index.m3u8", "1080/index.m3u8")
        )
    }

    @Test
    fun `resolveRelativeUrl keeps absolute url`() {
        assertEquals(
            "https://other.com/playlist.m3u8",
            durationResolver.resolveRelativeUrl("https://cdn/video/index.m3u8", "https://other.com/playlist.m3u8")
        )
    }
}
