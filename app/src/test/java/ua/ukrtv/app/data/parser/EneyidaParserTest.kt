package ua.ukrtv.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.ukrtv.app.data.providers.*
import java.io.File

class EneyidaParserTest {

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
    }
}
