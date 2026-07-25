package ua.ukrtv.app.player

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderQualityManagerScoreTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var manager: ProviderQualityManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.clear() } returns editor
        every { prefs.all } returns emptyMap()
        manager = ProviderQualityManager(mockk<OkHttpClient>(relaxed = true), context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getBestProvider returns null when cache is empty`() {
        assertNull(manager.getBestProvider())
    }

    @Test
    fun `getScore returns null when cache is empty`() {
        assertNull(manager.getScore("Eneyida"))
    }

    @Test
    fun `record then getScore returns valid score`() {
        val result = ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida",
            url = "https://test.com/stream",
            timeToFirstByteMs = 200,
            throughputKbps = 5000.0
        )
        manager.recordScore("Eneyida", result)

        val score = manager.getScore("Eneyida")
        assertNotNull(score)
        assertEquals("Eneyida", score!!.providerName)
        assertEquals(5000.0, score.throughputKbps, 0.01)
        assertEquals(200, score.timeToFirstByteMs)
    }

    @Test
    fun `record saves to SharedPreferences`() {
        val result = ProviderQualityManager.SpeedTestResult(
            providerName = "Uakino",
            url = "https://uakino.com/stream",
            timeToFirstByteMs = 300,
            throughputKbps = 3000.0
        )
        manager.recordScore("Uakino", result)

        verify { editor.putString(match { it.startsWith("score_Uakino") }, any()) }
        verify { editor.apply() }
    }

    @Test
    fun `getBestProvider returns fastest provider`() {
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url1", timeToFirstByteMs = 200, throughputKbps = 5000.0
        ))
        manager.recordScore("Uakino", ProviderQualityManager.SpeedTestResult(
            providerName = "Uakino", url = "url2", timeToFirstByteMs = 100, throughputKbps = 8000.0
        ))

        val best = manager.getBestProvider()
        assertNotNull(best)
        assertEquals("Uakino", best!!.providerName)
    }

    @Test
    fun `getBestProvider excludes specified provider`() {
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url1", timeToFirstByteMs = 200, throughputKbps = 5000.0
        ))
        manager.recordScore("Uakino", ProviderQualityManager.SpeedTestResult(
            providerName = "Uakino", url = "url2", timeToFirstByteMs = 100, throughputKbps = 8000.0
        ))

        val best = manager.getBestProvider(exclude = "Uakino")
        assertNotNull(best)
        assertEquals("Eneyida", best!!.providerName)
    }

    @Test
    fun `getScore returns null when score is expired`() {
        val oldTimestamp = System.currentTimeMillis() - (25L * 60 * 60 * 1000) // 25h ago
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url", timeToFirstByteMs = 200, throughputKbps = 5000.0, timestamp = oldTimestamp
        ))

        assertNull(manager.getScore("Eneyida"))
    }

    @Test
    fun `clear removes all scores from memory and prefs`() {
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url", timeToFirstByteMs = 200, throughputKbps = 5000.0
        ))
        manager.clearScores()

        assertNull(manager.getScore("Eneyida"))
        verify { editor.clear() }
        verify { editor.apply() }
    }

    @Test
    fun `clearProvider removes specific provider`() {
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url1", timeToFirstByteMs = 200, throughputKbps = 5000.0
        ))
        manager.recordScore("Uakino", ProviderQualityManager.SpeedTestResult(
            providerName = "Uakino", url = "url2", timeToFirstByteMs = 100, throughputKbps = 8000.0
        ))
        manager.clearProviderScore("Eneyida")

        assertNull(manager.getScore("Eneyida"))
        assertNotNull(manager.getScore("Uakino"))
        verify { editor.remove("score_Eneyida") }
        verify { editor.apply() }
    }

    @Test
    fun `markSlow halves throughput`() {
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url", timeToFirstByteMs = 200, throughputKbps = 5000.0
        ))
        manager.markProviderSlow("Eneyida")

        val score = manager.getScore("Eneyida")
        assertNotNull(score)
        assertEquals(2500.0, score!!.throughputKbps, 0.01)
    }

    @Test
    fun `loads valid scores from SharedPreferences on init`() {
        val scoreJson = json.encodeToString(ProviderQualityManager.ProviderScore(
            providerName = "Eneyida",
            url = "https://test.com/stream",
            throughputKbps = 5000.0,
            timeToFirstByteMs = 200
        ))
        every { prefs.all } returns mapOf("score_Eneyida" to scoreJson)

        val manager2 = ProviderQualityManager(mockk<OkHttpClient>(relaxed = true), context)
        val score = manager2.getScore("Eneyida")
        assertNotNull(score)
        assertEquals("Eneyida", score!!.providerName)
    }

    @Test
    fun `skips expired scores when loading from SharedPreferences`() {
        val oldScoreJson = json.encodeToString(ProviderQualityManager.ProviderScore(
            providerName = "Eneyida",
            url = "https://test.com/stream",
            throughputKbps = 5000.0,
            timeToFirstByteMs = 200,
            timestamp = System.currentTimeMillis() - (25L * 60 * 60 * 1000)
        ))
        every { prefs.all } returns mapOf("score_Eneyida" to oldScoreJson)

        val manager2 = ProviderQualityManager(mockk<OkHttpClient>(relaxed = true), context)
        assertNull(manager2.getScore("Eneyida"))
    }

    @Test
    fun `skips malformed entries when loading from SharedPreferences`() {
        every { prefs.all } returns mapOf("score_Eneyida" to "not-valid-json")

        val manager2 = ProviderQualityManager(mockk<OkHttpClient>(relaxed = true), context)
        assertNull(manager2.getScore("Eneyida"))
    }

    @Test
    fun `getBestProvider returns provider with positive throughput`() {
        manager.recordScore("Eneyida", ProviderQualityManager.SpeedTestResult(
            providerName = "Eneyida", url = "url1", timeToFirstByteMs = 200, throughputKbps = 0.0
        ))

        assertNull(manager.getBestProvider())
    }
}
