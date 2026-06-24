package ua.ukrtv.app.util

import org.junit.Assert.assertTrue
import org.junit.Test

class ContentUtilsTest {

    @Test
    fun romanNumerals_UkrainianII_shouldMatchLatinII() {
        val provider = "Мортал Комбат ІІ"
        val tmdb = "Mortal Kombат II"

        assertTrue(ContentUtils.isTitleMatch(provider, tmdb, strict = true))
        assertTrue(ContentUtils.isTitleMatch(provider, tmdb, strict = false))
    }
}


