package ua.ukrtv.app.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.data.providers.*

class DleParserDetailTest {

    @Test
    fun `uakino parseDetail extracts movie metadata from fi-item structure`() {
        val html = """
            <html><body>
                <h1>Тестовий фільм</h1>
                <div class="fi-item">
                    <div class="fi-label"><h2>Жанр</h2></div>
                    <div class="fi-desc"><a>бойовик</a>, <a>трилер</a></div>
                </div>
                <div class="fi-item">
                    <div class="fi-label"><h2>Країна</h2></div>
                    <div class="fi-desc"><a>Україна</a></div>
                </div>
                <div class="fi-item">
                    <div class="fi-label"><h2>Режисер</h2></div>
                    <div class="fi-desc"><a>Іван Петров</a></div>
                </div>
                <div class="fi-item">
                    <div class="fi-label"><h2>В ролях</h2></div>
                    <div class="fi-desc"><a>Актор 1</a>, <a>Актор 2</a></div>
                </div>
                <div class="fi-item">
                    <div class="fi-label">imdb рейтинг</div>
                    <div class="fi-desc">7.5</div>
                </div>
                <div id="full-text">Це опис тестового фільму, який має бути досить довгим, щоб пройти фільтр довжини. Тут йдеться про пригоди та захопливий сюжет.</div>
            </body></html>
        """.trimIndent()
        val parser = DleParser(UakinoProfile)
        val detail = parser.parseDetail(html, "https://uakino.best/filmy/online/test-film.html")

        assertEquals("Тестовий фільм", detail.title)
        assertEquals(listOf("бойовик", "трилер"), detail.genres)
        assertEquals(listOf("Україна"), detail.country)
        assertEquals(listOf("Іван Петров"), detail.director)
        assertEquals(listOf("Актор 1", "Актор 2"), detail.actors)
        assertEquals("7.5", detail.rating)
        assertTrue(detail.description.contains("тестового фільму"))
    }

    @Test
    fun `uakino parseDetail extracts rating from IMDB text`() {
        val html = """
            <html><body>
                <h1>Фільм IMDB</h1>
                <div class="fi-item">
                    <div class="fi-label">Рейтинг IMDB:</div>
                    <div class="fi-desc">8.5</div>
                </div>
                <div id="full-text">Довгий опис фільму, який має достатньо символів щоб пройти перевірку. Довгий опис фільму, який має достатньо символів щоб пройти перевірку.</div>
            </body></html>
        """.trimIndent()
        val parser = DleParser(UakinoProfile)
        val detail = parser.parseDetail(html, "https://uakino.best/filmy/online/imdb-film.html")

        assertEquals("8.5", detail.rating)
    }

    @Test
    fun `parseSearch filters blacklisted items`() {
        val html = """
            <html><body>
                <div class="short-item">
                    <a href="https://uakino.best/filmy/online/film1.html">
                        <span class="short-title">Фільм</span>
                    </a>
                </div>
                <div class="short-item">
                    <a href="https://uakino.best/filmy/online/top.html">
                        <span class="short-title">Топ фільмів</span>
                    </a>
                </div>
            </body></html>
        """.trimIndent()
        val parser = DleParser(UakinoProfile)
        val results = parser.parseSearch(html)

        assertEquals(1, results.size)
        assertEquals("Фільм", results[0].title)
    }
}
