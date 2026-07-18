package ua.ukrtv.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.local.entity.WatchProgressEntity
import ua.ukrtv.app.util.AppLogger

class WatchProgressRepositoryTest {

    private lateinit var dao: WatchProgressDao
    private lateinit var repository: WatchProgressRepository

    @Before
    fun setup() {
        mockkObject(AppLogger)
        every { AppLogger.d(any<String>(), any<String>()) } just Runs

        dao = mockk(relaxed = true)

        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        every { context.contentResolver } returns contentResolver
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), any()) } returns "test_device_id"

        repository = WatchProgressRepository(context, dao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveProgress with episodeId creates composite ID`() = runBlocking {
        repository.saveProgress("movie1", "s1e2", 5000L, 60000L)
        coVerify {
            dao.insert(match { it.id == "movie1_s1e2" && it.contentId == "movie1" && it.episodeId == "s1e2" })
        }
    }

    @Test
    fun `saveProgress without episodeId uses contentId as ID`() = runBlocking {
        repository.saveProgress("movie1", null, 5000L, 60000L)
        coVerify {
            dao.insert(match { it.id == "movie1" && it.contentId == "movie1" && it.episodeId == null })
        }
    }

    @Test
    fun `saveProgress preserves existing title when new title is empty`() = runBlocking {
        val existing = WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 1000L, durationMs = 60000L,
            title = "Existing Title", poster = "poster.jpg",
            pageUrl = "https://test.com", timestamp = System.currentTimeMillis() - 1000
        )
        coEvery { dao.getProgress("movie1") } returns existing

        repository.saveProgress("movie1", null, 5000L, 60000L, title = "")

        coVerify {
            dao.insert(match { it.title == "Existing Title" })
        }
    }

    @Test
    fun `saveProgress preserves existing poster when new poster is empty`() = runBlocking {
        val existing = WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 1000L, durationMs = 60000L,
            title = "Movie", poster = "old_poster.jpg",
            pageUrl = "https://test.com", timestamp = System.currentTimeMillis() - 1000
        )
        coEvery { dao.getProgress("movie1") } returns existing

        repository.saveProgress("movie1", null, 5000L, 60000L, poster = "")

        coVerify {
            dao.insert(match { it.poster == "old_poster.jpg" })
        }
    }

    @Test
    fun `saveProgress preserves existing streamUrl when new one is null`() = runBlocking {
        val existing = WatchProgressEntity(
            id = "movie1_s1e1", contentId = "movie1", episodeId = "s1e1",
            positionMs = 1000L, durationMs = 60000L,
            title = "Movie", poster = "poster.jpg",
            pageUrl = "https://test.com", timestamp = System.currentTimeMillis() - 1000,
            streamUrl = "https://cdn/old.m3u8", streamType = "HLS"
        )
        coEvery { dao.getProgress("movie1_s1e1") } returns existing

        repository.saveProgress("movie1", "s1e1", 5000L, 60000L, streamUrl = null)

        coVerify {
            dao.insert(match { it.streamUrl == "https://cdn/old.m3u8" })
        }
    }

    @Test
    fun `saveProgress uses new streamUrl when provided`() = runBlocking {
        val existing = WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 1000L, durationMs = 60000L,
            title = "Movie", poster = "poster.jpg",
            pageUrl = "https://test.com", timestamp = System.currentTimeMillis() - 1000,
            streamUrl = "https://cdn/old.m3u8", streamType = "HLS"
        )
        coEvery { dao.getProgress("movie1") } returns existing

        repository.saveProgress("movie1", null, 5000L, 60000L, streamUrl = "https://cdn/new.m3u8", streamType = "MPD")

        coVerify {
            dao.insert(match { it.streamUrl == "https://cdn/new.m3u8" && it.streamType == "MPD" })
        }
    }

    @Test
    fun `getProgress returns null for non-existent ID`() = runBlocking {
        coEvery { dao.getProgress("nonexistent") } returns null
        assertNull(repository.getProgress("nonexistent"))
    }

    @Test
    fun `getProgress with episodeId uses composite ID`() = runBlocking {
        coEvery { dao.getProgress("movie1_s1e2") } returns WatchProgressEntity(
            id = "movie1_s1e2", contentId = "movie1", episodeId = "s1e2",
            positionMs = 5000L, durationMs = 60000L,
            title = "Movie", poster = "poster.jpg",
            pageUrl = "https://test.com", timestamp = System.currentTimeMillis()
        )
        val result = repository.getProgress("movie1", "s1e2")
        assertNotNull(result)
        assertEquals("movie1", result!!.contentId)
        assertEquals("s1e2", result.episodeId)
    }

    @Test
    fun `getStreamCache returns null when streamUrl is null`() = runBlocking {
        coEvery { dao.getProgress("movie1") } returns WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 5000L, durationMs = 60000L,
            title = "Movie", poster = "", pageUrl = "https://test.com",
            timestamp = System.currentTimeMillis(), streamUrl = null, streamType = null
        )
        assertNull(repository.getStreamCache("movie1", null))
    }

    @Test
    fun `getStreamCache returns null when streamType is null`() = runBlocking {
        coEvery { dao.getProgress("movie1") } returns WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 5000L, durationMs = 60000L,
            title = "Movie", poster = "", pageUrl = "https://test.com",
            timestamp = System.currentTimeMillis(), streamUrl = "https://cdn/test.m3u8", streamType = null
        )
        assertNull(repository.getStreamCache("movie1", null))
    }

    @Test
    fun `getStreamCache returns null when expired beyond 24h`() = runBlocking {
        val expiredTimestamp = System.currentTimeMillis() - Constants.STREAM_DB_CACHE_TTL_MS - 1000
        coEvery { dao.getProgress("movie1") } returns WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 5000L, durationMs = 60000L,
            title = "Movie", poster = "", pageUrl = "https://test.com",
            timestamp = expiredTimestamp,
            streamUrl = "https://cdn/test.m3u8", streamType = "HLS"
        )
        assertNull(repository.getStreamCache("movie1", null))
    }

    @Test
    fun `getStreamCache returns Triple when valid and fresh`() = runBlocking {
        coEvery { dao.getProgress("movie1_s1e1") } returns WatchProgressEntity(
            id = "movie1_s1e1", contentId = "movie1", episodeId = "s1e1",
            positionMs = 5000L, durationMs = 60000L,
            title = "Movie", poster = "", pageUrl = "https://test.com",
            timestamp = System.currentTimeMillis(),
            streamUrl = "https://cdn/test.m3u8", streamType = "HLS",
            referer = "https://uakino.best/"
        )
        val result = repository.getStreamCache("movie1", "s1e1")
        assertNotNull(result)
        assertEquals("https://cdn/test.m3u8", result!!.first)
        assertEquals("HLS", result.second)
        assertEquals("https://uakino.best/", result.third)
    }

    @Test
    fun `getStreamCache defaults referer to empty when null`() = runBlocking {
        coEvery { dao.getProgress("movie1") } returns WatchProgressEntity(
            id = "movie1", contentId = "movie1", episodeId = null,
            positionMs = 5000L, durationMs = 60000L,
            title = "Movie", poster = "", pageUrl = "https://test.com",
            timestamp = System.currentTimeMillis(),
            streamUrl = "https://cdn/test.m3u8", streamType = "HLS",
            referer = null
        )
        val result = repository.getStreamCache("movie1", null)
        assertNotNull(result)
        assertEquals("", result!!.third)
    }

    @Test
    fun `getStreamCache returns null for non-existent entry`() = runBlocking {
        coEvery { dao.getProgress("nonexistent") } returns null
        assertNull(repository.getStreamCache("nonexistent", null))
    }

    @Test
    fun `deleteProgress calls deleteByContentId`() = runBlocking {
        repository.deleteProgress("movie1")
        coVerify { dao.deleteByContentId("movie1") }
    }

    @Test
    fun `cleanupOldEntries calls DAO with correct threshold`() = runBlocking {
        val before = System.currentTimeMillis()
        repository.cleanupOldEntries()
        val after = System.currentTimeMillis()

        coVerify {
            dao.deleteOlderThan(match { threshold ->
                val expectedMin = before - Constants.DB_CLEANUP_THRESHOLD_MS
                val expectedMax = after - Constants.DB_CLEANUP_THRESHOLD_MS
                threshold in expectedMin..expectedMax
            })
        }
    }

    @Test
    fun `getDeviceId returns device ID`() {
        assertEquals("test_device_id", repository.getDeviceId())
    }
}
