package ua.ukrtv.app.player

import io.mockk.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.media3.common.Player

class AudioEngineTest {

    private lateinit var audioEngine: AudioEngine
    private lateinit var mockPlayer: Player

    @Before
    fun setup() {
        mockPlayer = mockk(relaxed = true)
        audioEngine = AudioEngine()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial mode is NORMAL`() {
        assertEquals(AudioMode.NORMAL, audioEngine.getMode())
    }

    @Test
    fun `cycleMode NORMAL to NIGHT`() {
        val result = audioEngine.cycleMode()
        assertEquals(AudioMode.NIGHT, result)
        assertEquals(AudioMode.NIGHT, audioEngine.getMode())
    }

    @Test
    fun `cycleMode NIGHT to SITCOM`() {
        audioEngine.cycleMode()
        val result = audioEngine.cycleMode()
        assertEquals(AudioMode.SITCOM, result)
    }

    @Test
    fun `cycleMode SITCOM to NORMAL`() {
        audioEngine.cycleMode()
        audioEngine.cycleMode()
        val result = audioEngine.cycleMode()
        assertEquals(AudioMode.NORMAL, result)
    }

    @Test
    fun `cycleModeReverse NORMAL to SITCOM`() {
        val result = audioEngine.cycleModeReverse()
        assertEquals(AudioMode.SITCOM, result)
    }

    @Test
    fun `cycleModeReverse SITCOM to NIGHT`() {
        audioEngine.cycleModeReverse()
        val result = audioEngine.cycleModeReverse()
        assertEquals(AudioMode.NIGHT, result)
    }

    @Test
    fun `cycleModeReverse NIGHT to NORMAL`() {
        audioEngine.cycleModeReverse()
        audioEngine.cycleModeReverse()
        val result = audioEngine.cycleModeReverse()
        assertEquals(AudioMode.NORMAL, result)
    }

    @Test
    fun `setMode sets specific mode`() {
        audioEngine.setMode(AudioMode.SITCOM)
        assertEquals(AudioMode.SITCOM, audioEngine.getMode())
    }

    @Test
    fun `apply sets volume 1_0 for NORMAL when attached`() {
        audioEngine.attach(mockPlayer)
        verify { mockPlayer.volume = 1.0f }
    }

    @Test
    fun `apply sets volume 0_5 for NIGHT`() {
        audioEngine.setMode(AudioMode.NIGHT)
        audioEngine.attach(mockPlayer)
        verify { mockPlayer.volume = 0.5f }
    }

    @Test
    fun `apply sets volume 0_8 for SITCOM`() {
        audioEngine.setMode(AudioMode.SITCOM)
        audioEngine.attach(mockPlayer)
        verify { mockPlayer.volume = 0.8f }
    }

    @Test
    fun `cycleMode applies volume to attached player`() {
        audioEngine.attach(mockPlayer)
        clearMocks(mockPlayer)
        audioEngine.cycleMode()
        verify { mockPlayer.volume = 0.5f }
    }

    @Test
    fun `release resets mode to NORMAL`() {
        audioEngine.setMode(AudioMode.SITCOM)
        audioEngine.release()
        assertEquals(AudioMode.NORMAL, audioEngine.getMode())
    }

    @Test
    fun `release does not crash without player`() {
        audioEngine.release()
        assertEquals(AudioMode.NORMAL, audioEngine.getMode())
    }

    @Test
    fun `apply without player does not crash`() {
        audioEngine.setMode(AudioMode.NIGHT)
    }

    @Test
    fun `full forward cycle returns to NORMAL`() {
        audioEngine.cycleMode()
        audioEngine.cycleMode()
        audioEngine.cycleMode()
        assertEquals(AudioMode.NORMAL, audioEngine.getMode())
    }

    @Test
    fun `full reverse cycle returns to NORMAL`() {
        audioEngine.cycleModeReverse()
        audioEngine.cycleModeReverse()
        audioEngine.cycleModeReverse()
        assertEquals(AudioMode.NORMAL, audioEngine.getMode())
    }

    @Test
    fun `cycle and reverse are inverses`() {
        audioEngine.cycleMode()
        audioEngine.cycleModeReverse()
        assertEquals(AudioMode.NORMAL, audioEngine.getMode())
    }
}
