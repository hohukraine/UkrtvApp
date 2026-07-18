package ua.ukrtv.app.data.providers

import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Voiceover

class StreamProviderModelTest {

    // --- MediaSource.Movie ---

    @Test
    fun `Movie primaryUrl returns url`() {
        val movie = MediaSource.Movie("https://cdn.test/stream.m3u8")
        assertEquals("https://cdn.test/stream.m3u8", movie.primaryUrl)
    }

    @Test
    fun `Movie allUrls includes fallbacks`() {
        val movie = MediaSource.Movie(
            "https://cdn.test/stream.m3u8",
            fallbackUrls = listOf("https://cdn2.test/fallback.m3u8")
        )
        assertEquals(2, movie.allUrls.size)
        assertEquals("https://cdn.test/stream.m3u8", movie.allUrls[0])
        assertEquals("https://cdn2.test/fallback.m3u8", movie.allUrls[1])
    }

    @Test
    fun `Movie allUrls without fallbacks`() {
        val movie = MediaSource.Movie("https://cdn.test/stream.m3u8")
        assertEquals(1, movie.allUrls.size)
    }

    // --- MediaSource.Series ---

    @Test
    fun `Series primaryUrl returns first episode of first season`() {
        val series = MediaSource.Series(
            seasons = listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.test/s1e1.m3u8"),
                    ProviderEpisode(2, "Ep 2", "https://cdn.test/s1e2.m3u8")
                ))
            )
        )
        assertEquals("https://cdn.test/s1e1.m3u8", series.primaryUrl)
    }

    @Test
    fun `Series primaryUrl null when empty seasons`() {
        val series = MediaSource.Series(seasons = emptyList())
        assertNull(series.primaryUrl)
    }

    @Test
    fun `Series allUrls flattens all episodes`() {
        val series = MediaSource.Series(
            seasons = listOf(
                ProviderSeason(1, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.test/s1e1.m3u8"),
                    ProviderEpisode(2, "Ep 2", "https://cdn.test/s1e2.m3u8")
                )),
                ProviderSeason(2, listOf(
                    ProviderEpisode(1, "Ep 1", "https://cdn.test/s2e1.m3u8")
                ))
            )
        )
        assertEquals(3, series.allUrls.size)
    }

    // --- ProviderSeason ---

    @Test
    fun `ProviderSeason voiceovers groups by voiceover name`() {
        val season = ProviderSeason(1, listOf(
            ProviderEpisode(1, "Ep 1", "https://url1", voiceover = "UA"),
            ProviderEpisode(1, "Ep 1", "https://url2", voiceover = "RU"),
            ProviderEpisode(2, "Ep 2", "https://url3", voiceover = "UA")
        ))
        val vos = season.voiceovers
        assertEquals(2, vos.size)
        assertTrue(vos.any { it.name == "UA" })
        assertTrue(vos.any { it.name == "RU" })
    }

    @Test
    fun `ProviderSeason voiceovers returns Default when no voiceover tags`() {
        val season = ProviderSeason(1, listOf(
            ProviderEpisode(1, "Ep 1", "https://url1"),
            ProviderEpisode(2, "Ep 2", "https://url2")
        ))
        val vos = season.voiceovers
        assertEquals(1, vos.size)
        assertEquals("Default", vos[0].name)
        assertEquals(2, vos[0].episodes.size)
    }

    @Test
    fun `ProviderSeason voiceovers sorts episodes by number`() {
        val season = ProviderSeason(1, listOf(
            ProviderEpisode(3, "Ep 3", "url3", voiceover = "UA"),
            ProviderEpisode(1, "Ep 1", "url1", voiceover = "UA"),
            ProviderEpisode(2, "Ep 2", "url2", voiceover = "UA")
        ))
        val eps = season.voiceovers.first().episodes
        assertEquals(1, eps[0].number)
        assertEquals(2, eps[1].number)
        assertEquals(3, eps[2].number)
    }

    // --- toDomainSeason ---

    @Test
    fun `toDomainSeason converts correctly`() {
        val providerSeason = ProviderSeason(2, listOf(
            ProviderEpisode(1, "Ep 1", "https://url1", voiceover = "UA", poster = "p1.jpg"),
            ProviderEpisode(1, "Ep 1", "https://url2", voiceover = "UA"),
            ProviderEpisode(2, "Ep 2", "https://url3", voiceover = "UA")
        ), voiceoverOptions = listOf("UA"))

        val domainSeason = providerSeason.toDomainSeason(poster = "default.jpg")

        assertEquals(2, domainSeason.number)
        assertEquals(1, domainSeason.voiceovers.size)
        val ua = domainSeason.voiceovers[0]
        assertEquals("UA", ua.name)
        assertEquals(3, ua.episodes.size)
        assertEquals("https://url1", ua.episodes[0].url)
        assertEquals("p1.jpg", ua.episodes[0].poster)
        assertEquals("https://url3", ua.episodes[2].url)
    }

    @Test
    fun `toDomainSeason uses fallback poster when episode poster is empty`() {
        val providerSeason = ProviderSeason(1, listOf(
            ProviderEpisode(1, "Ep 1", "https://url1")
        ))
        val domainSeason = providerSeason.toDomainSeason(poster = "fallback.jpg")
        assertEquals("fallback.jpg", domainSeason.voiceovers[0].episodes[0].poster)
    }

    @Test
    fun `toDomainSeason preserves episode poster when set`() {
        val providerSeason = ProviderSeason(1, listOf(
            ProviderEpisode(1, "Ep 1", "https://url1", poster = "custom.jpg")
        ))
        val domainSeason = providerSeason.toDomainSeason(poster = "fallback.jpg")
        assertEquals("custom.jpg", domainSeason.voiceovers[0].episodes[0].poster)
    }
}
