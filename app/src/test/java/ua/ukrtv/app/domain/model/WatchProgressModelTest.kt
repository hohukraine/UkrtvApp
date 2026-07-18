package ua.ukrtv.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class WatchProgressModelTest {

    private fun wp(positionMs: Long, durationMs: Long) = WatchProgress(
        contentId = "test",
        episodeId = null,
        positionMs = positionMs,
        durationMs = durationMs
    )

    @Test
    fun `progressPercentage normal calculation`() {
        assertEquals(50, wp(5000, 10000).progressPercentage)
    }

    @Test
    fun `progressPercentage zero duration returns 0`() {
        assertEquals(0, wp(5000, 0).progressPercentage)
    }

    @Test
    fun `progressPercentage over 100 is clamped`() {
        assertEquals(100, wp(15000, 10000).progressPercentage)
    }

    @Test
    fun `progressPercentage exact 50 percent`() {
        assertEquals(50, wp(600000, 1200000).progressPercentage)
    }

    @Test
    fun `progressPercentage zero position returns 0`() {
        assertEquals(0, wp(0, 1200000).progressPercentage)
    }

    @Test
    fun `progressPercentage 94 percent within continue watching range`() {
        assertEquals(94, wp(940, 1000).progressPercentage)
    }

    @Test
    fun `progressPercentage 95 percent beyond continue watching range`() {
        assertEquals(95, wp(950, 1000).progressPercentage)
    }

    @Test
    fun `progressPercentage small values`() {
        assertEquals(1, wp(1, 100).progressPercentage)
    }

    @Test
    fun `progressPercentage both zero returns 0`() {
        assertEquals(0, wp(0, 0).progressPercentage)
    }
}
