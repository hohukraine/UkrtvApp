package ua.ukrtv.app.util

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ResolutionLoggerTest {

    @Before
    fun setUp() {
        mockkObject(AppLogger)
        every { AppLogger.d(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AppLogger)
    }

    @Test
    fun `log stores entry`() {
        val logger = ResolutionLogger()
        logger.log("https://test.com", "Strategy", "Test message")
        val logs = logger.getFormattedLogs()
        assertTrue(logs.contains("Test message"))
    }

    @Test
    fun `log stores error entry`() {
        val logger = ResolutionLogger()
        logger.log("https://test.com", "Strategy", "Error occurred", isError = true)
        val logs = logger.getFormattedLogs()
        assertTrue(logs.contains("Error occurred"))
    }

    @Test
    fun `log respects max capacity`() {
        val logger = ResolutionLogger()
        for (i in 1..60) {
            logger.log("https://test.com/$i", "Strategy", "Message $i")
        }
        val entries = logger.getEntries()
        assertEquals(50, entries.size)
        assertEquals("Message 60", entries.last().message)
    }

    @Test
    fun `getFormattedLogs returns all entries as string`() {
        val logger = ResolutionLogger()
        logger.log("https://test.com", "Strategy1", "First message")
        logger.log("https://test.com", "Strategy2", "Second message")
        val logs = logger.getFormattedLogs()
        assertTrue(logs.contains("First message"))
        assertTrue(logs.contains("Second message"))
    }

    @Test
    fun `getFormattedLogs returns empty for no entries`() {
        val logger = ResolutionLogger()
        assertEquals("", logger.getFormattedLogs())
    }

    @Test
    fun `getEntries returns entries in order`() {
        val logger = ResolutionLogger()
        logger.log("https://first.com", "S1", "First")
        logger.log("https://second.com", "S2", "Second")
        val entries = logger.getEntries()
        assertEquals(2, entries.size)
        assertEquals("First", entries[0].message)
        assertEquals("Second", entries[1].message)
    }

    @Test
    fun `clearLogs empties all entries`() {
        val logger = ResolutionLogger()
        logger.log("https://test.com", "Strategy", "Message")
        logger.clearLogs()
        assertEquals("", logger.getFormattedLogs())
    }

    @Test
    fun `log entry contains url and strategy`() {
        val logger = ResolutionLogger()
        logger.log("https://example.com", "DirectLink", "Resolved")
        val entries = logger.getEntries()
        assertEquals(1, entries.size)
        assertEquals("https://example.com", entries[0].url)
        assertEquals("DirectLink", entries[0].strategy)
    }
}
