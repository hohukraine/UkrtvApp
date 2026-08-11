package ua.ukrtv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitemapIndexParserTest {

    private fun sitemapXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <urlset>
            <url><loc>https://uafix.net/serials/serial-a/season-01-episode-01/</loc></url>
            <url><loc>https://uafix.net/serials/serial-a/season-01-episode-02/</loc></url>
            <url><loc>https://uafix.net/serials/serial-a/season-01-episode-10/</loc></url>
            <url><loc>https://uafix.net/serials/serial-a/season-02-episode-01/</loc></url>
            <url><loc>https://uafix.net/serials/serial-a/season-02-episode-02/</loc></url>
            <url><loc>https://uafix.net/serials/serial-a/season-02-episode-02/v1/</loc></url>
            <url><loc>https://uafix.net/serials/serial-b/season-01-episode-01/</loc></url>
            <url><loc>https://uafix.net/serials/serial-c/</loc></url>
            <url><loc>https://uafix.net/films/whatever/</loc></url>
        </urlset>
    """.trimIndent()

    @Test
    fun `parses season episode structure per slug`() {
        val data = SitemapIndexParser.parse(sitemapXml(), updatedAt = 123L, version = 2)

        assertEquals(2, data.version)
        assertEquals(123L, data.updatedAt)

        val serialA = data.uaflix["serial-a"]
        assertEquals(listOf(1, 2, 10), serialA?.get("1"))
        assertEquals(listOf(1, 2), serialA?.get("2"))

        val serialB = data.uaflix["serial-b"]
        assertEquals(listOf(1), serialB?.get("1"))

        assertNull(data.uaflix["serial-c"])
    }

    @Test
    fun `variant URLs are captured and counted in the episode list`() {
        val data = SitemapIndexParser.parse(sitemapXml())

        val variant = data.uaflixVariants["serial-a"]?.get("2")?.get("2")
        assertEquals("https://uafix.net/serials/serial-a/season-02-episode-02/v1/", variant)

        assertTrue(data.uaflix["serial-a"]!!["2"]!!.contains(2))
    }

    @Test
    fun `episode lists are sorted and deduplicated`() {
        val xml = """
            <urlset>
                <url><loc>https://uafix.net/serials/x/season-01-episode-05/</loc></url>
                <url><loc>https://uafix.net/serials/x/season-01-episode-02/</loc></url>
                <url><loc>https://uafix.net/serials/x/season-01-episode-02/</loc></url>
            </urlset>
        """.trimIndent()

        val data = SitemapIndexParser.parse(xml)

        assertEquals(listOf(2, 5), data.uaflix["x"]!!["1"])
    }

    @Test
    fun `non episode URLs and page links are ignored`() {
        val xml = """
            <urlset>
                <url><loc>https://uafix.net/search.html</loc></url>
                <url><loc>https://uafix.net/serials/</loc></url>
                <url><loc>https://uafix.net/serials/slug/season-01-episode-00/</loc></url>
            </urlset>
        """.trimIndent()

        val data = SitemapIndexParser.parse(xml)

        assertTrue(data.uaflix.isEmpty())
        assertTrue(data.uaflixVariants.isEmpty())
    }
}
