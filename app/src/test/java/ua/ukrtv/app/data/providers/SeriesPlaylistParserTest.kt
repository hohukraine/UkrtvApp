package ua.ukrtv.app.data.providers

import org.junit.Assert.*
import org.junit.Test

class SeriesPlaylistParserTest {

    // --- extractBalancedJson ---

    @Test
    fun `extractBalancedJson extracts complete array`() {
        val text = """Some prefix [{"a":1}] some suffix"""
        val result = SeriesPlaylistParser.extractBalancedJson(text)
        assertNotNull(result)
        assertEquals("[{\"a\":1}]", result)
    }

    @Test
    fun `extractBalancedJson handles nested arrays`() {
        val text = """[1, [2, 3], 4]"""
        val result = SeriesPlaylistParser.extractBalancedJson(text)
        assertNotNull(result)
        assertEquals("[1, [2, 3], 4]", result)
    }

    @Test
    fun `extractBalancedJson handles strings with brackets`() {
        val text = """[{"title": "[test]"}]"""
        val result = SeriesPlaylistParser.extractBalancedJson(text)
        assertNotNull(result)
    }

    @Test
    fun `extractBalancedJson returns null when no array`() {
        val text = "No array here"
        assertNull(SeriesPlaylistParser.extractBalancedJson(text))
    }

    @Test
    fun `extractBalancedJson returns null for unclosed bracket`() {
        val text = """[1, 2, 3"""
        assertNull(SeriesPlaylistParser.extractBalancedJson(text))
    }

    @Test
    fun `extractBalancedJson handles escaped quotes`() {
        val text = """[{"title": "he said \"hello\""}]"""
        val result = SeriesPlaylistParser.extractBalancedJson(text)
        assertNotNull(result)
    }

    // --- parseUrlBasedSeries ---

    @Test
    fun `parseUrlBasedSeries HDVB pattern`() {
        val media = listOf(
            "https://s1.example.com/s1e1/index.m3u8",
            "https://s1.example.com/s1e2/index.m3u8",
            "https://s1.example.com/s1e3/index.m3u8"
        )
        val result = SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "TestProvider")
        assertNotNull(result)
        assertEquals(1, result!!.seasons.size)
        assertEquals(1, result.seasons[0].number)
        assertEquals(3, result.seasons[0].episodes.size)
    }

    @Test
    fun `parseUrlBasedSeries multiple seasons via HDVB`() {
        val media = listOf(
            "https://s1.example.com/s1e1/index.m3u8",
            "https://s1.example.com/s1e2/index.m3u8",
            "https://s2.example.com/s2e1/index.m3u8",
            "https://s2.example.com/s2e2/index.m3u8"
        )
        val result = SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "TestProvider")
        assertNotNull(result)
        assertEquals(2, result!!.seasons.size)
    }

    @Test
    fun `parseUrlBasedSeries fallback to flat list when more than 2 items`() {
        val media = listOf(
            "https://cdn.example.com/part1.m3u8",
            "https://cdn.example.com/part2.m3u8",
            "https://cdn.example.com/part3.m3u8"
        )
        val result = SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "TestProvider")
        assertNotNull(result)
        assertEquals(1, result!!.seasons.size)
        assertEquals(3, result.seasons[0].episodes.size)
    }

    @Test
    fun `parseUrlBasedSeries returns null for single item`() {
        val media = listOf("https://cdn.example.com/movie.m3u8")
        assertNull(SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "TestProvider"))
    }

    @Test
    fun `parseUrlBasedSeries returns null for two items`() {
        val media = listOf(
            "https://cdn.example.com/part1.m3u8",
            "https://cdn.example.com/part2.m3u8"
        )
        assertNull(SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "TestProvider"))
    }

    @Test
    fun `parseUrlBasedSeries preserves providerName and pageUrl`() {
        val media = listOf(
            "https://cdn.example.com/s1e1/index.m3u8",
            "https://cdn.example.com/s1e2/index.m3u8",
            "https://cdn.example.com/s1e3/index.m3u8"
        )
        val result = SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "MyProvider")
        assertNotNull(result)
        assertEquals("MyProvider", result!!.providerName)
        assertEquals("https://page.html", result.referer)
    }

    // --- parseAjaxPlaylistHtml ---

    @Test
    fun `parseAjaxPlaylistHtml extracts data-file links`() {
        val html = """
            <div class="playlists-videos">
                <div class="playlists-items">
                    <li data-file="https://cdn.example.com/ep1.m3u8">1 Серія</li>
                    <li data-file="https://cdn.example.com/ep2.m3u8">2 Серія</li>
                </div>
            </div>
        """.trimIndent()
        val (episodes, voNames) = SeriesPlaylistParser.parseAjaxPlaylistHtml(html)
        assertEquals(2, episodes.size)
        assertEquals(1, episodes[0].number)
        assertEquals(2, episodes[1].number)
    }

    @Test
    fun `parseAjaxPlaylistHtml uses protocol-relative URL`() {
        val html = """
            <div class="playlists-videos">
                <div class="playlists-items">
                    <li data-file="//cdn.example.com/ep1.m3u8">1 Серія</li>
                </div>
            </div>
        """.trimIndent()
        val (episodes, _) = SeriesPlaylistParser.parseAjaxPlaylistHtml(html)
        assertEquals(1, episodes.size)
        assertTrue(episodes[0].url.startsWith("https:"))
    }

    @Test
    fun `parseAjaxPlaylistHtml returns empty for empty HTML`() {
        val (episodes, voNames) = SeriesPlaylistParser.parseAjaxPlaylistHtml("")
        assertTrue(episodes.isEmpty())
        assertTrue(voNames.isEmpty())
    }

    @Test
    fun `parseAjaxPlaylistHtml returns empty for no playlist divs`() {
        val (episodes, voNames) = SeriesPlaylistParser.parseAjaxPlaylistHtml("<div>no playlist here</div>")
        assertTrue(episodes.isEmpty())
    }

    // --- parseJsonPlaylist ---

    @Test
    fun `parseJsonPlaylist returns null for invalid JSON`() {
        assertNull(SeriesPlaylistParser.parseJsonPlaylist("not json", "https://page.html", "Test"))
    }

    @Test
    fun `parseJsonPlaylist returns null for empty array`() {
        assertNull(SeriesPlaylistParser.parseJsonPlaylist("[]", "https://page.html", "Test"))
    }

    @Test
    fun `parseJsonPlaylist handles direct voiceover structure`() {
        val json = """
        [
            {
                "title": "UA",
                "folder": [
                    {"title": "1 Серія", "file": "https://cdn.example.com/ep1.m3u8"},
                    {"title": "2 Серія", "file": "https://cdn.example.com/ep2.m3u8"}
                ]
            }
        ]
        """.trimIndent()
        val result = SeriesPlaylistParser.parseJsonPlaylist(json, "https://page.html", "Test")
        assertNotNull(result)
        assertEquals(1, result!!.seasons.size)
        assertEquals(2, result.seasons[0].episodes.size)
    }

    @Test
    fun `parseJsonPlaylist handles season with voiceovers`() {
        val json = """
        [
            {
                "title": "1 сезон",
                "folder": [
                    {
                        "title": "UA",
                        "folder": [
                            {"title": "1 Серія", "file": "https://cdn.example.com/s1e1.m3u8"}
                        ]
                    }
                ]
            }
        ]
        """.trimIndent()
        val result = SeriesPlaylistParser.parseJsonPlaylist(json, "https://page.html", "Test")
        assertNotNull(result)
        assertEquals(1, result!!.seasons.size)
        assertEquals(1, result.seasons[0].episodes.size)
    }

    // --- ProviderSeason.voiceovers grouping edge cases ---

    @Test
    fun `ProviderSeason with mixed voiceover tags`() {
        val season = ProviderSeason(1, listOf(
            ProviderEpisode(1, "Ep 1", "url1", voiceover = "UA"),
            ProviderEpisode(1, "Ep 1", "url2", voiceover = "RU"),
            ProviderEpisode(2, "Ep 2", "url3", voiceover = "UA"),
            ProviderEpisode(2, "Ep 2", "url4", voiceover = "RU")
        ))
        val vos = season.voiceovers
        assertEquals(2, vos.size)
        val ua = vos.find { it.name == "UA" }
        val ru = vos.find { it.name == "RU" }
        assertNotNull(ua)
        assertNotNull(ru)
        assertEquals(2, ua!!.episodes.size)
        assertEquals(2, ru!!.episodes.size)
    }

    @Test
    fun `ProviderSeason with Default voiceover`() {
        val season = ProviderSeason(1, listOf(
            ProviderEpisode(1, "Ep 1", "url1"),
            ProviderEpisode(2, "Ep 2", "url2")
        ))
        val vos = season.voiceovers
        assertEquals(1, vos.size)
        assertEquals("Default", vos[0].name)
    }

    // --- parseUrlBasedSeries with path pattern ---

    @Test
    fun `parseUrlBasedSeries path pattern seasons and episodes`() {
        val media = listOf(
            "https://cdn.example.com/1/1/index.m3u8",
            "https://cdn.example.com/1/2/index.m3u8",
            "https://cdn.example.com/2/1/index.m3u8"
        )
        val result = SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "Test")
        assertNotNull(result)
        assertTrue(result!!.seasons.size >= 1)
    }

    @Test
    fun `parseUrlBasedSeries path pattern with high season numbers`() {
        val media = listOf(
            "https://cdn.example.com/2024/1/1/index.m3u8",
            "https://cdn.example.com/2024/1/2/index.m3u8"
        )
        val result = SeriesPlaylistParser.parseUrlBasedSeries(media, "https://page.html", "Test")
        assertNotNull(result)
        assertEquals(1, result!!.seasons.size)
        assertEquals(1, result.seasons[0].number)
    }
}
