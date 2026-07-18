package ua.ukrtv.app.player

import org.junit.Assert.*
import org.junit.Test

class ExternalPlayerResultTest {

    @Test
    fun `VLC full episode - finished via 90 percent threshold`() {
        val result = ExternalPlayerLauncher.parseResult(1341972L, 1341972L, null)
        assertTrue(result.isFinished)
        assertEquals(1341972L, result.positionMs)
        assertEquals(1341972L, result.durationMs)
    }

    @Test
    fun `VLC returns 0 slash 0 - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(0L, 0L, null)
        assertFalse(result.isFinished)
        assertEquals(0L, result.positionMs)
        assertEquals(0L, result.durationMs)
    }

    @Test
    fun `end_by playback_completion - finished`() {
        val result = ExternalPlayerLauncher.parseResult(1200000L, 1344766L, "playback_completion")
        assertTrue(result.isFinished)
    }

    @Test
    fun `end_by playback_completion with zero pos - finished`() {
        val result = ExternalPlayerLauncher.parseResult(0L, 0L, "playback_completion")
        assertTrue(result.isFinished)
    }

    @Test
    fun `end_by exit - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(5000L, 1200000L, "exit")
        assertFalse(result.isFinished)
        assertEquals(5000L, result.positionMs)
        assertEquals(1200000L, result.durationMs)
    }

    @Test
    fun `end_by exit at start - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(0L, 1341972L, "exit")
        assertFalse(result.isFinished)
    }

    @Test
    fun `MX Player partial watch - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(3685L, 1344766L, null)
        assertFalse(result.isFinished)
        assertEquals(3685L, result.positionMs)
        assertEquals(1344766L, result.durationMs)
    }

    @Test
    fun `MX Player near end - finished via 90 percent`() {
        val result = ExternalPlayerLauncher.parseResult(1300000L, 1344766L, null)
        assertTrue(result.isFinished)
    }

    @Test
    fun `duration only - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(0L, 1200000L, null)
        assertFalse(result.isFinished)
    }

    @Test
    fun `no data - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(0L, 0L, null)
        assertFalse(result.isFinished)
    }

    @Test
    fun `exactly 90 percent - finished`() {
        val result = ExternalPlayerLauncher.parseResult(900L, 1000L, null)
        assertTrue(result.isFinished)
    }

    @Test
    fun `just below 90 percent - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(899L, 1000L, null)
        assertFalse(result.isFinished)
    }

    @Test
    fun `unknown end_by string - falls through to threshold`() {
        val result = ExternalPlayerLauncher.parseResult(1300000L, 1344766L, "user_exit")
        assertTrue(result.isFinished)
    }

    @Test
    fun `unknown end_by with no duration - not finished`() {
        val result = ExternalPlayerLauncher.parseResult(0L, 0L, "unknown")
        assertFalse(result.isFinished)
    }

    @Test
    fun `VLC position without duration - preserves position`() {
        val result = ExternalPlayerLauncher.parseResult(1338962L, 0L, null)
        assertFalse(result.isFinished)
        assertEquals(1338962L, result.positionMs)
        assertEquals(0L, result.durationMs)
    }
}
