package ua.ukrtv.app.ui.player

import android.content.Context
import android.content.Intent
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.local.entity.WatchProgressEntity
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.domain.model.Voiceover
import ua.ukrtv.app.player.*
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PlayerPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var savedStateHandle: androidx.lifecycle.SavedStateHandle
    private lateinit var watchProgressRepository: WatchProgressRepository
    private lateinit var streamResolver: StreamResolver
    private lateinit var playerPreferences: PlayerPreferences
    private lateinit var audioEngine: AudioEngine
    private lateinit var thermalMonitor: ThermalMonitor
    private lateinit var providerManager: ProviderManager
    private lateinit var playerFactory: PlayerFactory

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        mockkObject(AppLogger)
        every { AppLogger.d(any<String>(), any<String>()) } just Runs
        every { AppLogger.i(any<String>(), any<String>()) } just Runs
        every { AppLogger.e(any<String>(), any<String>()) } just Runs
        every { AppLogger.w(any<String>(), any<String>(), any<Throwable>()) } just Runs

        savedStateHandle = androidx.lifecycle.SavedStateHandle()
        val mockDao = mockk<WatchProgressDao>(relaxed = true)
        val mockContext = mockk<Context>(relaxed = true)
        watchProgressRepository = WatchProgressRepository(mockContext, mockDao)
        streamResolver = mockk(relaxed = true)
        playerPreferences = mockk(relaxed = true)
        audioEngine = AudioEngine()
        thermalMonitor = mockk(relaxed = true)
        every { thermalMonitor.thermalStatus } returns emptyFlow()
        providerManager = mockk(relaxed = true)
        playerFactory = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): PlayerViewModel {
        return PlayerViewModel(
            appContext = mockk(relaxed = true),
            savedStateHandle = savedStateHandle,
            watchProgressRepository = watchProgressRepository,
            okHttpClient = mockk(relaxed = true),
            streamResolver = streamResolver,
            playerFactory = playerFactory,
            thermalMonitor = thermalMonitor,
            audioEngine = audioEngine,
            providerManager = providerManager,
            playerPreferences = playerPreferences
        )
    }

    private fun ep(number: Int) = Episode(number, "Ep $number", "https://test/$number")
    private fun vo(name: String, vararg episodes: Episode) = Voiceover(name, episodes.toList())
    private fun season(number: Int, vararg episodes: Episode) = Season(number, listOf(vo("UA", *episodes)))

    private fun setField(vm: PlayerViewModel, fieldName: String, value: Any?) {
        val field = PlayerViewModel::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(vm, value)
    }

    private fun getField(vm: PlayerViewModel, fieldName: String): Any? {
        val field = PlayerViewModel::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(vm)
    }

    // --- prepareNextEpisode ---

    @Test
    fun `prepareNextEpisode advances S1E1 to S1E2`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2), ep(3))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        assertTrue(vm.prepareNextEpisode())
        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
        assertEquals("s1e2", getField(vm, "episodeId"))
    }

    @Test
    fun `prepareNextEpisode advances S1E2 to S1E3`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2), ep(3))))
        setField(vm, "season", 1)
        setField(vm, "episode", 2)

        assertTrue(vm.prepareNextEpisode())
        assertEquals(1, getField(vm, "season"))
        assertEquals(3, getField(vm, "episode"))
    }

    @Test
    fun `prepareNextEpisode advances S1E2 to S2E1 in multi-season`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(
            season(1, ep(1), ep(2)),
            season(2, ep(1), ep(2))
        ))
        setField(vm, "season", 1)
        setField(vm, "episode", 2)

        assertTrue(vm.prepareNextEpisode())
        assertEquals(2, getField(vm, "season"))
        assertEquals(1, getField(vm, "episode"))
    }

    @Test
    fun `prepareNextEpisode returns false at last episode`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2), ep(3))))
        setField(vm, "season", 1)
        setField(vm, "episode", 3)

        assertFalse(vm.prepareNextEpisode())
    }

    @Test
    fun `prepareNextEpisode returns false with empty seasons`() {
        val vm = createViewModel()
        setField(vm, "seasons", emptyList<Season>())
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        assertFalse(vm.prepareNextEpisode())
    }

    @Test
    fun `prepareNextEpisode returns false with null season`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1))))
        setField(vm, "season", null)
        setField(vm, "episode", 1)

        assertFalse(vm.prepareNextEpisode())
    }

    @Test
    fun `prepareNextEpisode returns false with null episode`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1))))
        setField(vm, "season", 1)
        setField(vm, "episode", null)

        assertFalse(vm.prepareNextEpisode())
    }

    @Test
    fun `prepareNextEpisode saves to savedStateHandle`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        vm.prepareNextEpisode()

        assertEquals(1, savedStateHandle.get<Int>("ext_season"))
        assertEquals(2, savedStateHandle.get<Int>("ext_episode"))
    }

    // --- hasNextEpisode / hasPreviousEpisode ---

    @Test
    fun `hasNextEpisode returns true when more episodes`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        assertTrue(vm.hasNextEpisode())
    }

    @Test
    fun `hasNextEpisode returns false at end`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        assertFalse(vm.hasNextEpisode())
    }

    @Test
    fun `hasPreviousEpisode returns true when earlier episodes`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 2)

        assertTrue(vm.hasPreviousEpisode())
    }

    @Test
    fun `hasPreviousEpisode returns false at start`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        assertFalse(vm.hasPreviousEpisode())
    }

    @Test
    fun `hasNextEpisode false with null season`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", null)
        setField(vm, "episode", 1)

        assertFalse(vm.hasNextEpisode())
    }

    // --- handleExternalPlayerResult ---

    @Test
    fun `handleExternalPlayerResult with null result returns Error`() {
        val vm = createViewModel()
        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns null

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.Error, result)
    }

    @Test
    fun `handleExternalPlayerResult 0 slash 0 not finished returns NoData`() {
        val vm = createViewModel()
        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(0L, 0L, false)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.NoData, result)
    }

    @Test
    fun `handleExternalPlayerResult full episode with seasons returns Advanced`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
    }

    @Test
    fun `handleExternalPlayerResult finished but no next episode returns NotFinished`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertTrue(result is ExternalPlayerReturnResult.NotFinished)
    }

    @Test
    fun `handleExternalPlayerResult not finished returns NotFinished`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(30000L, 60000L, false)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertTrue(result is ExternalPlayerReturnResult.NotFinished)
        val nf = result as ExternalPlayerReturnResult.NotFinished
        assertEquals(30000L, nf.positionMs)
        assertEquals(60000L, nf.durationMs)
    }

    @Test
    fun `handleExternalPlayerResult VLC 90 percent finished via threshold`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(54000L, 60000L, false)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
    }

    @Test
    fun `handleExternalPlayerResult null season episode defaults to S1E1 then advances`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2)), season(2, ep(1))))
        setField(vm, "season", null)
        setField(vm, "episode", null)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
    }

    @Test
    fun `handleExternalPlayerResult no seasons returns NotFinished even when finished`() {
        val vm = createViewModel()
        setField(vm, "seasons", emptyList<Season>())
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertTrue(result is ExternalPlayerReturnResult.NotFinished)
    }

    @Test
    fun `handleExternalPlayerResult VLC exit returns NotFinished with position`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerResult(10000L, 60000L, false)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertTrue(result is ExternalPlayerReturnResult.NotFinished)
        assertEquals(10000L, (result as ExternalPlayerReturnResult.NotFinished).positionMs)
    }

    // --- saveProgress dedup ---

    @Test
    fun `saveProgress skips when position unchanged and positive`() {
        val vm = createViewModel()
        setField(vm, "contentId", "test_content")
        setField(vm, "episodeId", "s1e1")
        setField(vm, "title", "Test")
        setField(vm, "poster", "")
        setField(vm, "pageUrl", "https://test.com")
        setField(vm, "referer", "")
        setField(vm, "lastSavedPosition", 5000L)
        setField(vm, "availableStreams", mutableListOf("https://cdn/test.m3u8"))

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://cdn/test.m3u8", title = "Test", subtitle = "",
                positionMs = 5000L, referer = "", streamType = StreamType.HLS
            )
        )

        vm.saveProgress(5000L, 60000L)

        assertEquals(5000L, getField(vm, "lastSavedPosition"))
    }

    @Test
    fun `saveProgress saves when position changes`() {
        val vm = createViewModel()
        setField(vm, "contentId", "test_content")
        setField(vm, "episodeId", "s1e1")
        setField(vm, "title", "Test")
        setField(vm, "poster", "")
        setField(vm, "pageUrl", "https://test.com")
        setField(vm, "referer", "")
        setField(vm, "lastSavedPosition", 5000L)
        setField(vm, "availableStreams", mutableListOf("https://cdn/test.m3u8"))

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://cdn/test.m3u8", title = "Test", subtitle = "",
                positionMs = 6000L, referer = "", streamType = StreamType.HLS
            )
        )

        vm.saveProgress(6000L, 60000L)

        assertEquals(6000L, getField(vm, "lastSavedPosition"))
    }

    @Test
    fun `saveProgress skips when duration is zero`() {
        val vm = createViewModel()
        setField(vm, "lastSavedPosition", -1L)

        vm.saveProgress(5000L, 0L)

        assertEquals(-1L, getField(vm, "lastSavedPosition"))
    }

    // --- initialize state ---

    @Test
    fun `initialize sets season and episode from params`() {
        val vm = createViewModel()

        vm.initialize("test_id", "Test Movie", "https://test.com", season = 2, episode = 3, poster = "poster.jpg")

        assertEquals("test_id", getField(vm, "contentId"))
        assertEquals("Test Movie", getField(vm, "title"))
        assertEquals(2, getField(vm, "season"))
        assertEquals(3, getField(vm, "episode"))
        assertEquals("s2e3", getField(vm, "episodeId"))
    }

    @Test
    fun `initialize with null season and episode`() {
        val vm = createViewModel()

        vm.initialize("test_id", "Test Movie", "https://test.com")

        assertNull(getField(vm, "season"))
        assertNull(getField(vm, "episode"))
        assertNull(getField(vm, "episodeId"))
    }

    // --- switchToBuiltInPlayer ---

    @Test
    fun `switchToBuiltInPlayer sets position in state`() {
        val vm = createViewModel()

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://cdn/test.m3u8", title = "Test", subtitle = "",
                positionMs = 0L, referer = "", streamType = StreamType.HLS
            )
        )

        vm.switchToBuiltInPlayer(15000L)

        val status = stateFlow.value.status as PlayerStatus.Ready
        assertEquals(15000L, status.positionMs)
    }
}
