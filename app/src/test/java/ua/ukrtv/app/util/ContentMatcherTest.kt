package ua.ukrtv.app.util

import org.junit.Assert.*
import org.junit.Test

import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.MovieDetail

class ContentMatcherTest {

    @Test
    fun `isTitleMatch matches Ukrainian roman numeral to latin roman numeral`() {
        val p = "Мортал Комбат ІІ"
        val t = "Mortal Kombat II"

        assertTrue(ContentUtils.isTitleMatch(p, t, strict = true))
        assertTrue(ContentUtils.isTitleMatch(p, t, strict = false))
    }
}


