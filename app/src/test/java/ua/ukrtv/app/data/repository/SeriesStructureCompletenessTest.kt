package ua.ukrtv.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesStructureCompletenessTest {

    @Test
    fun `null index count is treated as complete`() {
        assertTrue(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = null, cachedEpisodeCount = 10))
        assertTrue(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = null, cachedEpisodeCount = null))
    }

    @Test
    fun `null cached count is treated as complete (nothing cached to invalidate)`() {
        assertTrue(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = 26, cachedEpisodeCount = null))
    }

    @Test
    fun `cache with fewer episodes than index is incomplete`() {
        assertFalse(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = 26, cachedEpisodeCount = 20))
        assertFalse(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = 142, cachedEpisodeCount = 121))
    }

    @Test
    fun `cache equal to or larger than index is complete`() {
        assertTrue(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = 26, cachedEpisodeCount = 26))
        assertTrue(SeriesStructureCompleteness.isCacheComplete(indexedEpisodeCount = 26, cachedEpisodeCount = 30))
    }
}
