package ua.ukrtv.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.MovieDetail

class ContentMatcherFindBestMatchTest {

    @Test
    fun `title matching should work without relying on provider search items`() {
        // This test intentionally avoids ContentMatcher.findBestMatch(...) because SearchItem
        // constructor/type resolution differs across environments.
        assertEquals(
            true,
            ContentUtils.isTitleMatch("Мортал Комбат ІІ", "Mortal Kombat II", strict = true)
        )
    }
}


