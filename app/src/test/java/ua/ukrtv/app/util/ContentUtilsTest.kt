package ua.ukrtv.app.util

import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.data.providers.ContentUtils

class ContentUtilsTest {

    @Test
    fun `cleanTitle removes technical suffixes`() {
        assertEquals("Фільм", ContentUtils.cleanTitle("Фільм HD 1080p BDRip"))
    }

    @Test
    fun `cleanTitle handles text after slash`() {
        assertEquals("Назва", ContentUtils.cleanTitle("Оригінал / Назва"))
    }

    @Test
    fun `cleanTitle removes parasites`() {
        assertEquals("Фільм", ContentUtils.cleanTitle("Фільм дивитися онлайн українською"))
    }

    @Test
    fun `cleanTitle removes year in parentheses`() {
        assertEquals("Фільм", ContentUtils.cleanTitle("Фільм (2024)"))
    }

    @Test
    fun `cleanTitle removes stop markers`() {
        assertEquals("Фільм", ContentUtils.cleanTitle("Фільм Жанр: бойовик Рік виходу: 2024"))
    }

    @Test
    fun `cleanTitle handles HTML entities`() {
        assertEquals("Фільм Серіал", ContentUtils.cleanTitle("Фільм &amp; Серіал"))
    }

    @Test
    fun `cleanTitle returns blank for blank input`() {
        assertEquals("", ContentUtils.cleanTitle("  "))
    }

    @Test
    fun `cleanTitle trims whitespace`() {
        assertEquals("Фільм", ContentUtils.cleanTitle("  Фільм  "))
    }

    @Test
    fun `cleanTitle deduplicates consecutive duplicate words`() {
        assertEquals("фільм", ContentUtils.cleanTitle("фільм Фільм"))
    }
}
