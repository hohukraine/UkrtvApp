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
    fun `eneyida parseSearch returns results from parseList`() {
        val html = """
            <html><body>
                <article class="short">
                    <a class="short_title" href="https://eneyida.tv/111-film.html">Знайдений фільм</a>
                    <a class="short_img"><img src="https://eneyida.tv/poster.jpg"></a>
                </article>
            </body></html>
        """.trimIndent()
        val parser = DleParser(EneyidaProfile)
        val results = parser.parseSearch(html)

        assertTrue(results.isNotEmpty())
        assertEquals("Знайдений фільм", results[0].title)
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

    @Test
    fun `eneyida parseComments parses comm-two fallback`() {
        val html = """
            <html><body>
                <div class="comm-item">
                    <span class="comm-author">Женя Харків</span>
                    <div class="comm-left img-box"><img src="https://eneyida.tv/avatar.jpg"></div>
                    <div class="comm-two full-text">Гарний серіал, дуже сподобався!</div>
                    <div class="comm-date">Сьогодні, 15:30</div>
                </div>
            </body></html>
        """.trimIndent()
        val parser = DleParser(EneyidaProfile)
        val doc = org.jsoup.Jsoup.parse(html)
        val comments = parser.parseComments(doc)

        assertEquals(1, comments.size)
        assertEquals("Женя Харків", comments[0].author)
        assertEquals("Сьогодні, 15:30", comments[0].date)
    }

    @Test
    fun `eneyida parseDetail extracts duration`() {
        val html = """
            <html><body>
                <h1>Фільм з тривалістю</h1>
                <ul class="full_info">
                    <li>Тривалість: 01:45:00</li>
                </ul>
                <div class="full-text">Досить довгий опис фільму з тривалістю для тестування фільтру тексту.</div>
            </body></html>
        """.trimIndent()
        val parser = DleParser(EneyidaProfile)
        val detail = parser.parseDetail(html, "https://eneyida.tv/12345-duration-test.html")

        assertEquals("01:45:00", detail.duration)
    }

    @Test
    fun `parseDetail extracts seasonCount from text`() {
        val html = """
            <html><body>
                <h1>Серіал 4 сезон</h1>
                <ul class="full_info">
                    <li>Статус: 4 сезон 6 серія</li>
                </ul>
                <div class="full-text">Досить довгий опис серіалу з багатьма сезонами.</div>
            </body></html>
        """.trimIndent()
        val parser = DleParser(EneyidaProfile)
        val detail = parser.parseDetail(html, "https://eneyida.tv/12345-season-count-test.html")

        assertEquals(4, detail.seasonCount)
    }
}
