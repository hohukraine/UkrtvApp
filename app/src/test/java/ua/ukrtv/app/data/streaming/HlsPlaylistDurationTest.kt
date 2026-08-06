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

    @Test
    fun `parseMpdDuration reads mediaPresentationDuration`() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD mediaPresentationDuration="PT1H23M45.5S" type="static">
              <Period id="0" start="PT0S">
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="1" bandwidth="3000000" width="1920" height="1080"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        assertEquals(5_025_500L, durationResolver.parseMpdDuration(content))
    }

    @Test
    fun `parseMpdDuration sums segment timeline when no presentation duration`() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1000" duration="2000">
                    <SegmentTimeline>
                      <S t="0" d="2000" r="2"/>
                      <S t="6000" d="2000"/>
                      <S t="8000" d="1500"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="1" bandwidth="3000000" width="1920" height="1080"/>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <SegmentTemplate timescale="48000" duration="96000">
                    <SegmentTimeline>
                      <S t="0" d="96000" r="2"/>
                      <S t="288000" d="96000"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="2" bandwidth="128000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        // Video timeline: (2000*3) + 2000 + 1500 = 9500 ms at timescale 1000
        assertEquals(9_500L, durationResolver.parseMpdDuration(content))
    }

    @Test
    fun `parseIso8601Duration parses hours minutes seconds`() {
        assertEquals(5_025_500L, durationResolver.parseIso8601Duration("PT1H23M45.5S"))
        assertEquals(120_000L, durationResolver.parseIso8601Duration("PT2M"))
        assertEquals(45_000L, durationResolver.parseIso8601Duration("PT45S"))
        assertEquals(3_600_000L, durationResolver.parseIso8601Duration("PT1H"))
    }

    @Test
    fun `parseIso8601Duration rejects malformed values`() {
        assertEquals(null, durationResolver.parseIso8601Duration("P1D"))
        assertEquals(null, durationResolver.parseIso8601Duration(""))
        assertEquals(null, durationResolver.parseIso8601Duration("PT0S"))
    }

    @Test
    fun `sumMpdSegments returns null when no segments present`() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="1" bandwidth="3000000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        assertEquals(null, durationResolver.sumMpdSegments(content))
    }
}
