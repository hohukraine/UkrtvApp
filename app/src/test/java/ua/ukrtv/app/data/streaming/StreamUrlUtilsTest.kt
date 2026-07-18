package ua.ukrtv.app.data.streaming

import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.domain.model.StreamType

class StreamUrlUtilsTest {

    // --- isForbiddenUrl ---

    @Test
    fun `isForbiddenUrl youtube com`() = assertTrue(isForbiddenUrl("https://youtube.com/watch?v=123"))

    @Test
    fun `isForbiddenUrl youtu be`() = assertTrue(isForbiddenUrl("https://youtu.be/123"))

    @Test
    fun `isForbiddenUrl youtube-nocookie`() = assertTrue(isForbiddenUrl("https://youtube-nocookie.com/embed/123"))

    @Test
    fun `isForbiddenUrl trailer`() = assertTrue(isForbiddenUrl("https://cdn.example.com/trailer.mp4"))

    @Test
    fun `isForbiddenUrl preview`() = assertTrue(isForbiddenUrl("https://cdn.example.com/preview.mp4"))

    @Test
    fun `isForbiddenUrl embed`() = assertTrue(isForbiddenUrl("https://cdn.example.com/embed/player"))

    @Test
    fun `isForbiddenUrl Ukrainian trailer`() = assertTrue(isForbiddenUrl("https://cdn.example.com/трейлер.mp4"))

    @Test
    fun `isForbiddenUrl watch query`() = assertTrue(isForbiddenUrl("https://youtube.com/watch?v=abc"))

    @Test
    fun `isForbiddenUrl shorts`() = assertTrue(isForbiddenUrl("https://youtube.com/shorts/abc"))

    @Test
    fun `isForbiddenUrl valid m3u8 not forbidden`() = assertFalse(isForbiddenUrl("https://cdn.example.com/stream.m3u8"))

    @Test
    fun `isForbiddenUrl empty string`() = assertFalse(isForbiddenUrl(""))

    // --- isDirectStreamUrl ---

    @Test
    fun `isDirectStreamUrl m3u8`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/stream.m3u8"))

    @Test
    fun `isDirectStreamUrl mpd`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/stream.mpd"))

    @Test
    fun `isDirectStreamUrl mp4`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/stream.mp4"))

    @Test
    fun `isDirectStreamUrl webm`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/stream.webm"))

    @Test
    fun `isDirectStreamUrl with query params`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/stream.m3u8?token=abc"))

    @Test
    fun `isDirectStreamUrl with fragment`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/stream.m3u8#segment"))

    @Test
    fun `isDirectStreamUrl html not direct`() = assertFalse(isDirectStreamUrl("https://example.com/page.html"))

    @Test
    fun `isDirectStreamUrl empty`() = assertFalse(isDirectStreamUrl(""))

    @Test
    fun `isDirectStreamUrl case insensitive`() = assertTrue(isDirectStreamUrl("https://cdn.example.com/STREAM.M3U8"))

    // --- isVodIdUrl ---

    @Test
    fun `isVodIdUrl with vod path`() = assertTrue(isVodIdUrl("https://example.com/vod/12345"))

    @Test
    fun `isVodIdUrl dleid scheme`() = assertTrue(isVodIdUrl("dleid://12345"))

    @Test
    fun `isVodIdUrl vod with direct m3u8 is not vod`() = assertFalse(isVodIdUrl("https://example.com/vod/stream.m3u8"))

    @Test
    fun `isVodIdUrl regular page not vod`() = assertFalse(isVodIdUrl("https://example.com/page.html"))

    // --- getStreamType ---

    @Test
    fun `getStreamType m3u8 is HLS`() = assertEquals(StreamType.HLS, getStreamType("https://cdn.example.com/stream.m3u8"))

    @Test
    fun `getStreamType mpd is MPD`() = assertEquals(StreamType.MPD, getStreamType("https://cdn.example.com/stream.mpd"))

    @Test
    fun `getStreamType mp4 is MP4`() = assertEquals(StreamType.MP4, getStreamType("https://cdn.example.com/stream.mp4"))

    @Test
    fun `getStreamType with query is HLS`() = assertEquals(StreamType.HLS, getStreamType("https://cdn.example.com/stream.m3u8?token=abc"))

    @Test
    fun `getStreamType with fragment is HLS`() = assertEquals(StreamType.HLS, getStreamType("https://cdn.example.com/stream.m3u8#v=1"))

    @Test
    fun `getStreamType webm is MP4`() = assertEquals(StreamType.MP4, getStreamType("https://cdn.example.com/stream.webm"))

    @Test
    fun `getStreamType unknown is MP4`() = assertEquals(StreamType.MP4, getStreamType("https://cdn.example.com/stream.avi"))

    // --- inferReferer ---

    @Test
    fun `inferReferer eneyida`() = assertEquals("https://eneyida.tv/", inferReferer("https://eneyida.tv/stream.m3u8"))

    @Test
    fun `inferReferer hdvbua`() = assertEquals("https://eneyida.tv/", inferReferer("https://s11.hdvbua.pro/media/stream.m3u8"))

    @Test
    fun `inferReferer uakino`() = assertEquals("https://uakino.best/", inferReferer("https://uakino.best/stream.m3u8"))

    @Test
    fun `inferReferer ashdi`() = assertEquals("https://uakino.best/", inferReferer("https://ashdi.vip/stream.m3u8"))

    @Test
    fun `inferReferer unknown returns empty`() = assertEquals("", inferReferer("https://cdn.example.com/stream.m3u8"))
}
