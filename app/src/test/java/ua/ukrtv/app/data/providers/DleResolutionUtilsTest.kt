package ua.ukrtv.app.data.providers

import org.junit.Assert.*
import org.junit.Test

class DleResolutionUtilsTest {

    // --- findMediaUrlsInText ---

    @Test
    fun `findMediaUrlsInText extracts m3u8 URLs`() {
        val html = """file: "https://cdn.example.com/video/stream.m3u8?token=abc" """
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("stream.m3u8"))
    }

    @Test
    fun `findMediaUrlsInText extracts mp4 URLs`() {
        val html = """<source src="https://cdn.example.com/movie.mp4" type="video/mp4">"""
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("movie.mp4"))
    }

    @Test
    fun `findMediaUrlsInText extracts webm URLs`() {
        val html = """src='https://cdn.example.com/video.webm'"""
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("video.webm"))
    }

    @Test
    fun `findMediaUrlsInText extracts master m3u8`() {
        val html = """playlist: "https://cdn.example.com/hls/master.m3u8" """
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertTrue(urls.any { it.contains("master.m3u8") })
    }

    @Test
    fun `findMediaUrlsInText extracts dleid`() {
        val html = """file: "dleid://12345" """
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertEquals(1, urls.size)
        assertEquals("dleid://12345", urls[0])
    }

    @Test
    fun `findMediaUrlsInText extracts multiple URLs`() {
        val html = """
            file: "https://cdn.example.com/stream.m3u8"
            file: "https://cdn2.example.com/video.mp4"
        """
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertEquals(2, urls.size)
    }

    @Test
    fun `findMediaUrlsInText returns empty for plain text`() {
        val urls = DleResolutionUtils.findMediaUrlsInText("no media urls here")
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `findMediaUrlsInText returns empty for empty input`() {
        assertTrue(DleResolutionUtils.findMediaUrlsInText("").isEmpty())
    }

    @Test
    fun `findMediaUrlsInText extracts hdvbua m3u8 from Playerjs`() {
        val html = """
            <script>
            var player = new Playerjs({
                id: "player_95973",
                file: "https://s30.hdvbua.pro/media3/hls/films/argylle_2024_webdl_1080p_95973/hls/index.m3u8",
                subtitle: "[Українські]https://s30.hdvbua.pro/media3/player/subtitle/95973_ua.vtt",
            });
            </script>
        """.trimIndent()
        val urls = DleResolutionUtils.findMediaUrlsInText(html)
        assertTrue(urls.any { it.contains("index.m3u8") })
        assertEquals(1, urls.size)
        assertEquals("https://s30.hdvbua.pro/media3/hls/films/argylle_2024_webdl_1080p_95973/hls/index.m3u8", urls[0])
    }

    // --- extractSeasonNum ---

    @Test
    fun `extractSeasonNum from Russian label`() {
        assertEquals(2, DleResolutionUtils.extractSeasonNum("2 сезон"))
    }

    @Test
    fun `extractSeasonNum from Ukrainian label`() {
        assertEquals(1, DleResolutionUtils.extractSeasonNum("1 сезон"))
    }

    @Test
    fun `extractSeasonNum from English label`() {
        assertEquals(3, DleResolutionUtils.extractSeasonNum("Season 3"))
    }

    @Test
    fun `extractSeasonNum from sNN pattern`() {
        assertEquals(4, DleResolutionUtils.extractSeasonNum("Серія s04e02"))
    }

    @Test
    fun `extractSeasonNum from label before number`() {
        assertEquals(5, DleResolutionUtils.extractSeasonNum("сезон 5"))
    }

    @Test
    fun `extractSeasonNum removes year before parsing`() {
        assertEquals(2, DleResolutionUtils.extractSeasonNum("2022 season 2"))
    }

    @Test
    fun `extractSeasonNum returns null for no match`() {
        assertNull(DleResolutionUtils.extractSeasonNum("just a movie"))
    }

    @Test
    fun `extractSeasonNum returns null for empty`() {
        assertNull(DleResolutionUtils.extractSeasonNum(""))
    }

    // --- ensureAbsoluteUrl ---

    @Test
    fun `ensureAbsoluteUrl keeps absolute URL`() {
        val url = "https://cdn.example.com/stream.m3u8"
        assertEquals(url, DleResolutionUtils.ensureAbsoluteUrl(url, "https://base.com/"))
    }

    @Test
    fun `ensureAbsoluteUrl adds https for protocol-relative`() {
        assertEquals("https://cdn.example.com/stream.m3u8", DleResolutionUtils.ensureAbsoluteUrl("//cdn.example.com/stream.m3u8", "https://base.com/"))
    }

    @Test
    fun `ensureAbsoluteUrl creates dleid for numeric`() {
        assertEquals("dleid://12345", DleResolutionUtils.ensureAbsoluteUrl("12345", "https://base.com/"))
    }

    @Test
    fun `ensureAbsoluteUrl handles relative path`() {
        val result = DleResolutionUtils.ensureAbsoluteUrl("/video/stream.m3u8", "https://example.com/page.html")
        assertEquals("https://example.com/video/stream.m3u8", result)
    }

    @Test
    fun `ensureAbsoluteUrl resolves relative to base`() {
        val result = DleResolutionUtils.ensureAbsoluteUrl("stream.m3u8", "https://example.com/folder/page.html")
        assertEquals("https://example.com/folder/stream.m3u8", result)
    }

    @Test
    fun `ensureAbsoluteUrl keeps dleid as-is`() {
        assertEquals("dleid://12345", DleResolutionUtils.ensureAbsoluteUrl("dleid://12345", "https://base.com/"))
    }

    // --- parseAjaxPlaylistHtml ---

    @Test
    fun `parseAjaxPlaylistHtml handles voiceover tabs and data-file items`() {
        val html = """<div class="playlists-lists"><ul class="playlists-items"><li>DniproFilm (1-8)</li></ul></div>
            <div class="playlists-videos">
                <div class="playlists-items">
                    <li data-file="//ashdi.vip/vod/123">1 серія</li>
                    <li data-file="//ashdi.vip/vod/456">2 серія</li>
                </div>
            </div>"""
        val (episodes, voices) = SeriesPlaylistParser.parseAjaxPlaylistHtml(html)
        assertEquals(1, voices.size)
        assertEquals("DniproFilm", voices[0])
        assertEquals(2, episodes.size)
        assertEquals("https://ashdi.vip/vod/123", episodes[0].url)
    }

    @Test
    fun `parseAjaxPlaylistHtml returns empty for empty input`() {
        val (episodes, voices) = SeriesPlaylistParser.parseAjaxPlaylistHtml("<div></div>")
        assertTrue(episodes.isEmpty())
        assertTrue(voices.isEmpty())
    }
}
