package ua.ukrtv.app.ui.player

import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Voiceover

class EpisodeNavigatorTest {

    private fun ep(number: Int, url: String = "https://test/$number") = Episode(number, "Ep $number", url)
    private fun vo(name: String, vararg episodes: Episode) = Voiceover(name, episodes.toList())
    private fun season(number: Int, vararg episodes: Episode) = Season(number, listOf(vo("UA", *episodes)))

    private val singleSeason = listOf(
        season(1, ep(1), ep(2), ep(3))
    )

    private val multiSeason = listOf(
        season(1, ep(1), ep(2)),
        season(2, ep(1), ep(2), ep(3))
    )

    private val threeSeasons = listOf(
        season(1, ep(1)),
        season(2, ep(1), ep(2)),
        season(3, ep(1))
    )

    // --- nextEpisode ---

    @Test
    fun `nextEpisode single season - E1 to E2`() {
        val result = EpisodeNavigator.nextEpisode(singleSeason, 1, 1)
        assertNotNull(result)
        assertEquals(1, result!!.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun `nextEpisode single season - E2 to E3`() {
        val result = EpisodeNavigator.nextEpisode(singleSeason, 1, 2)
        assertNotNull(result)
        assertEquals(1, result!!.season)
        assertEquals(3, result.episode)
    }

    @Test
    fun `nextEpisode single season - last episode returns null`() {
        val result = EpisodeNavigator.nextEpisode(singleSeason, 1, 3)
        assertNull(result)
    }

    @Test
    fun `nextEpisode multi season - S1E2 to S2E1`() {
        val result = EpisodeNavigator.nextEpisode(multiSeason, 1, 2)
        assertNotNull(result)
        assertEquals(2, result!!.season)
        assertEquals(1, result.episode)
    }

    @Test
    fun `nextEpisode multi season - S2E1 to S2E2`() {
        val result = EpisodeNavigator.nextEpisode(multiSeason, 2, 1)
        assertNotNull(result)
        assertEquals(2, result!!.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun `nextEpisode multi season - last episode of last season returns null`() {
        val result = EpisodeNavigator.nextEpisode(multiSeason, 2, 3)
        assertNull(result)
    }

    @Test
    fun `nextEpisode three seasons - S2E2 to S3E1`() {
        val result = EpisodeNavigator.nextEpisode(threeSeasons, 2, 2)
        assertNotNull(result)
        assertEquals(3, result!!.season)
        assertEquals(1, result.episode)
    }

    @Test
    fun `nextEpisode null seasons returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(null, 1, 1))
    }

    @Test
    fun `nextEpisode null currentSeason returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(singleSeason, null, 1))
    }

    @Test
    fun `nextEpisode null currentEpisode returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(singleSeason, 1, null))
    }

    @Test
    fun `nextEpisode all null returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(null, null, null))
    }

    @Test
    fun `nextEpisode season not found returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(singleSeason, 99, 1))
    }

    @Test
    fun `nextEpisode episode not found returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(singleSeason, 1, 99))
    }

    @Test
    fun `nextEpisode empty seasons list returns null`() {
        assertNull(EpisodeNavigator.nextEpisode(emptyList(), 1, 1))
    }

    @Test
    fun `nextEpisode single episode per season - S1E1 to S2E1`() {
        val seasons = listOf(season(1, ep(1)), season(2, ep(1)), season(3, ep(1)))
        val result = EpisodeNavigator.nextEpisode(seasons, 1, 1)
        assertNotNull(result)
        assertEquals(2, result!!.season)
        assertEquals(1, result.episode)
    }

    @Test
    fun `nextEpisode single episode per season - last returns null`() {
        val seasons = listOf(season(1, ep(1)), season(2, ep(1)))
        assertNull(EpisodeNavigator.nextEpisode(seasons, 2, 1))
    }

    // --- previousEpisode ---

    @Test
    fun `previousEpisode single season - E3 to E2`() {
        val result = EpisodeNavigator.previousEpisode(singleSeason, 1, 3)
        assertNotNull(result)
        assertEquals(1, result!!.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun `previousEpisode single season - E2 to E1`() {
        val result = EpisodeNavigator.previousEpisode(singleSeason, 1, 2)
        assertNotNull(result)
        assertEquals(1, result!!.season)
        assertEquals(1, result.episode)
    }

    @Test
    fun `previousEpisode single season - first episode returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(singleSeason, 1, 1))
    }

    @Test
    fun `previousEpisode multi season - S2E1 to S1E2`() {
        val result = EpisodeNavigator.previousEpisode(multiSeason, 2, 1)
        assertNotNull(result)
        assertEquals(1, result!!.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun `previousEpisode three seasons - S3E1 to S2E2`() {
        val result = EpisodeNavigator.previousEpisode(threeSeasons, 3, 1)
        assertNotNull(result)
        assertEquals(2, result!!.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun `previousEpisode first of first season returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(multiSeason, 1, 1))
    }

    @Test
    fun `previousEpisode null seasons returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(null, 1, 1))
    }

    @Test
    fun `previousEpisode null currentSeason returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(singleSeason, null, 1))
    }

    @Test
    fun `previousEpisode null currentEpisode returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(singleSeason, 1, null))
    }

    @Test
    fun `previousEpisode empty list returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(emptyList(), 1, 1))
    }

    @Test
    fun `previousEpisode season not found returns null`() {
        assertNull(EpisodeNavigator.previousEpisode(singleSeason, 99, 1))
    }

    @Test
    fun `previousEpisode single episode per season - S2E1 to S1E1`() {
        val seasons = listOf(season(1, ep(1)), season(2, ep(1)))
        val result = EpisodeNavigator.previousEpisode(seasons, 2, 1)
        assertNotNull(result)
        assertEquals(1, result!!.season)
        assertEquals(1, result.episode)
    }

    // --- hasNextEpisode ---

    @Test
    fun `hasNextEpisode true when more episodes exist`() {
        assertTrue(EpisodeNavigator.hasNextEpisode(singleSeason, 1, 1))
    }

    @Test
    fun `hasNextEpisode false at last episode`() {
        assertFalse(EpisodeNavigator.hasNextEpisode(singleSeason, 1, 3))
    }

    @Test
    fun `hasNextEpisode false with null params`() {
        assertFalse(EpisodeNavigator.hasNextEpisode(singleSeason, null, null))
    }

    // --- hasPreviousEpisode ---

    @Test
    fun `hasPreviousEpisode true when earlier episodes exist`() {
        assertTrue(EpisodeNavigator.hasPreviousEpisode(singleSeason, 1, 2))
    }

    @Test
    fun `hasPreviousEpisode false at first episode`() {
        assertFalse(EpisodeNavigator.hasPreviousEpisode(singleSeason, 1, 1))
    }

    @Test
    fun `hasPreviousEpisode false with null params`() {
        assertFalse(EpisodeNavigator.hasPreviousEpisode(singleSeason, null, null))
    }

    // --- cross-season boundary ---

    @Test
    fun `nextEpisode returns null when next season is empty`() {
        val seasons = listOf(
            season(1, ep(1)),
            Season(2, listOf(vo("UA"))),
            season(3, ep(1))
        )
        val result = EpisodeNavigator.nextEpisode(seasons, 1, 1)
        assertNull(result)
    }

    @Test
    fun `previousEpisode returns null when prev season is empty`() {
        val seasons = listOf(
            Season(1, listOf(vo("UA"))),
            season(2, ep(1))
        )
        val result = EpisodeNavigator.previousEpisode(seasons, 2, 1)
        assertNull(result)
    }
}
