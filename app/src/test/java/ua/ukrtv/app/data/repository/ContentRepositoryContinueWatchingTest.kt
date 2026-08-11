package ua.ukrtv.app.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.ukrtv.app.domain.model.WatchProgress

class ContentRepositoryContinueWatchingTest {

    private fun watchProgress(
        contentId: String,
        title: String,
        positionMs: Long,
        durationMs: Long = 0L,
        poster: String = "https://cdn/poster.jpg",
        pageUrl: String = "https://uakino.club/film/$contentId",
        episodeId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) = WatchProgress(
        contentId = contentId,
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        title = title,
        poster = poster,
        pageUrl = pageUrl,
        timestamp = timestamp
    )

    private fun createRepo(progress: List<WatchProgress>): ContentRepository {
        val watchProgressRepo = mockk<WatchProgressRepository>(relaxed = true)
        coEvery { watchProgressRepo.getAllProgress() } returns flowOf(progress)
        return ContentRepository(
            providerManager = mockk(relaxed = true),
            watchProgressRepository = watchProgressRepo,
            streamResolver = mockk(relaxed = true),
            htmlCacheDao = mockk(relaxed = true),
            seriesStructureDao = mockk(relaxed = true),
            seriesIndexRepository = mockk(relaxed = true),
            homeCacheRepository = mockk(relaxed = true),
            catalogRepository = mockk(relaxed = true),
            tmdbTrendsRepository = mockk(relaxed = true)
        )
    }

    @Test
    fun `same film from different providers is deduplicated`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("u1", "Форсаж 10", 60_000L, 1_200_000L),
                watchProgress("a1", "Форсаж 10 (2023)", 30_000L, 1_200_000L, pageUrl = "https://uaflix.net/film/a1")
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(1, movies.size)
        assertTrue(movies[0].id == "u1" || movies[0].id == "a1")
    }

    @Test
    fun `film with position but unknown duration is visible`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("m1", "Кінг Конг (2005)", 600_000L, durationMs = 0L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(1, movies.size)
        assertEquals(0, movies[0].watchProgress)
    }

    @Test
    fun `short watch with unknown duration is visible`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("m1", "Кінг Конг (2005)", 5_000L, durationMs = 0L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(1, movies.size)
        assertEquals(0, movies[0].watchProgress)
    }

    @Test
    fun `zero position is hidden`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("m1", "Кінг Конг (2005)", 0L, durationMs = 0L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertTrue(movies.isEmpty())
    }

    @Test
    fun `same film title with different years stays as two cards`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("m1", "Кінг Конг (2005)", 600_000L, 6_000_000L),
                watchProgress("m2", "Кінг Конг (2017)", 600_000L, 6_000_000L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(2, movies.size)
    }

    @Test
    fun `blank titles are never merged`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("m1", "", 600_000L, 6_000_000L),
                watchProgress("m2", "", 600_000L, 6_000_000L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(2, movies.size)
    }

    @Test
    fun `pre-resolved next episode placeholder is hidden`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("s1", "Гра престолів", 60_000L, 3_600_000L, episodeId = "s1e3"),
                watchProgress("s1", "", 0L, 0L, poster = "", episodeId = "s1e4", timestamp = System.currentTimeMillis() + 1_000L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(1, movies.size)
        assertEquals(3, movies[0].episode)
    }

    @Test
    fun `posterless placeholder does not duplicate the watched series card`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("s1", "Гра престолів", 30_000L, 3_600_000L, episodeId = "s1e1"),
                watchProgress("s1", "", 0L, 0L, poster = "", episodeId = "s1e2", timestamp = System.currentTimeMillis() + 1_000L)
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(1, movies.size)
        assertEquals("s1", movies[0].id)
        assertEquals(1, movies[0].episode)
        assertTrue(movies[0].poster.isNotEmpty())
    }

    @Test
    fun `posterless entry is dropped when a posterized duplicate exists`() = runTest {
        val repo = createRepo(
            listOf(
                watchProgress("m1", "Дюна", 60_000L, 6_000_000L, poster = ""),
                watchProgress("m2", "Дюна (2021)", 60_000L, 6_000_000L, poster = "https://cdn/poster.jpg")
            )
        )

        val movies = repo.getContinueWatching().first()

        assertEquals(1, movies.size)
        assertEquals("m2", movies[0].id)
    }

    @Test
    fun `continueWatchingTitleKey ignores year in parens and case`() {
        val repo = createRepo(emptyList())
        assertEquals(
            repo.continueWatchingTitleKey(watchProgress("m1", "Форсаж 10 (2023)", 60_000L, 1_200_000L)),
            repo.continueWatchingTitleKey(watchProgress("m2", "форсаж 10", 60_000L, 1_200_000L))
        )
    }

    @Test
    fun `continueWatchingTitleKey falls back to contentId for blank titles`() {
        val repo = createRepo(emptyList())
        val key1 = repo.continueWatchingTitleKey(watchProgress("m1", "", 60_000L, 6_000_000L))
        val key2 = repo.continueWatchingTitleKey(watchProgress("m2", "", 60_000L, 6_000_000L))
        assertTrue(key1 != key2)
    }

    @Test
    fun `watchProgressPercentage uses stored duration`() {
        val progress = watchProgress("m1", "Тест", positionMs = 300_000L, durationMs = 600_000L)
        assertEquals(50, progress.progressPercentage)
    }
}
