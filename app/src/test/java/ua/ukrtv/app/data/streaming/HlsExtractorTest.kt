package ua.ukrtv.app.data.streaming

import android.util.Base64
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.domain.model.StreamType

class HlsExtractorTest {

    private lateinit var extractor: HlsExtractor

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } returns byteArrayOf()

        extractor = HlsExtractor()
    }

    @Test
    fun `extractFromHtml finds m3u8 URLs`() {
        val html = """https://cdn.example.com/stream.m3u8"""
        val results = extractor.extractFromHtml(html)
        assertEquals(1, results.size)
        assertEquals("https://cdn.example.com/stream.m3u8", results[0].url)
        assertEquals(StreamType.HLS, results[0].type)
    }

    @Test
    fun `extractFromHtml finds mpd URLs`() {
        val html = """https://cdn.example.com/manifest.mpd"""
        val results = extractor.extractFromHtml(html)
        assertEquals(1, results.size)
        assertEquals("https://cdn.example.com/manifest.mpd", results[0].url)
        assertEquals(StreamType.MPD, results[0].type)
    }

    @Test
    fun `extractFromHtml finds mp4 URLs`() {
        val html = """https://cdn.example.com/video.mp4"""
        val results = extractor.extractFromHtml(html)
        assertEquals(1, results.size)
        assertEquals("https://cdn.example.com/video.mp4", results[0].url)
        assertEquals(StreamType.MP4, results[0].type)
    }

    @Test
    fun `extractFromHtml normalizes protocol-relative URLs`() {
        val html = """//cdn.example.com/stream.m3u8"""
        val results = extractor.extractFromHtml(html)
        assertEquals(1, results.size)
        assertEquals("https://cdn.example.com/stream.m3u8", results[0].url)
    }

    @Test
    fun `extractFromHtml extracts with quality label`() {
        val html = """[1080p] https://cdn.example.com/hd.m3u8"""
        val results = extractor.extractFromHtml(html)
        val match = results.find { it.label == "1080p" }
        assertNotNull(match)
        assertEquals(1080, match!!.quality)
    }

    @Test
    fun `extractFromHtml extracts quality label in parentheses`() {
        val html = """(720p) https://cdn.example.com/med.m3u8"""
        val results = extractor.extractFromHtml(html)
        val match = results.find { it.label == "720p" }
        assertNotNull(match)
        assertEquals(720, match!!.quality)
    }

    @Test
    fun `extractFromHtml extracts quality with colon separator`() {
        val html = """480p: https://cdn.example.com/low.m3u8"""
        val results = extractor.extractFromHtml(html)
        assertTrue(results.any { it.label == "480p" })
    }

    @Test
    fun `extractFromHtml infers quality from URL`() {
        val html = """https://cdn.example.com/720p/stream.m3u8"""
        val results = extractor.extractFromHtml(html)
        val match = results.find { it.label == "720p" }
        assertNotNull(match)
    }

    @Test
    fun `extractFromHtml deduplicates by URL`() {
        val html = """
            https://cdn.example.com/stream.m3u8
            https://cdn.example.com/stream.m3u8?token=abc
        """
        val results = extractor.extractFromHtml(html)
        assertEquals(2, results.size)
    }

    @Test
    fun `extractFromHtml sorts by quality descending`() {
        val html = """
            (360p) https://cdn.example.com/low.m3u8
            (1080p) https://cdn.example.com/hd.m3u8
            (720p) https://cdn.example.com/med.m3u8
        """
        val results = extractor.extractFromHtml(html)
        assertTrue(results.first().quality >= results.last().quality)
    }

    @Test
    fun `extractFromHtml returns empty for plain text`() {
        assertTrue(extractor.extractFromHtml("no media here").isEmpty())
    }

    @Test
    fun `extractFromHtml returns empty for empty input`() {
        assertTrue(extractor.extractFromHtml("").isEmpty())
    }
}
