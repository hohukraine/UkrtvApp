package ua.ukrtv.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.ukrtv.app.data.providers.*
import java.io.File

class EneyidaParserTest {

    @Test
    fun testParseEneyidaHome() {
        val file = File("../../eneyida.html")
        if (!file.exists()) {
             println("Test skipped: eneyida.html not found at ${file.absolutePath}")
             return
        }
        val html = file.readText()
        
        val parser = DleParser(EneyidaProfile)
        val movies = parser.parseList(html)
        
        println("Found ${movies.size} items in eneyida.html")
        assertTrue(movies.isNotEmpty())
        
        val from = movies.find { it.title.contains("Ззовні") }
        if (from != null) {
            println("Found From: ${from.title} -> ${from.pageUrl}")
            // In eneyida.html: <a class="short_title" id="short_title" href="https://eneyida.tv/7026-zzovni.html">Ззовні</a>
            assertEquals("https://eneyida.tv/7026-zzovni.html", from.pageUrl)
        }
    }

    @Test
    fun testEneyidaSeriesDetection() {
         val html = """
            <article class="short">
                <a class="short_title" href="https://eneyida.tv/7026-zzovni.html">Ззовні</a>
                <div class="metaBottom label_quel-camrip">4 сезон 6 серія</div>
            </article>
         """.trimIndent()
         
         val parser = DleParser(EneyidaProfile)
         val movies = parser.parseList(html)
         assertEquals(1, movies.size)
         assertEquals(ua.ukrtv.app.domain.model.ContentType.SERIES, movies[0].type)
    }
}
