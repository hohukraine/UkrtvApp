package ua.ukrtv.app.ui.player

import android.content.Context
import android.content.Intent
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.domain.model.Voiceover
import ua.ukrtv.app.player.AudioEngine
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.player.MediaPrefetcher
import ua.ukrtv.app.player.PlayerFactory
import ua.ukrtv.app.player.PlayerPool
import ua.ukrtv.app.player.ProviderScoreCache
import ua.ukrtv.app.player.ProviderSpeedTester
import ua.ukrtv.app.player.StreamHealthMonitor
import ua.ukrtv.app.player.ThermalMonitor
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
    private lateinit var playerPool: PlayerPool
    private lateinit var mediaPrefetcher: MediaPrefetcher
    private lateinit var providerSpeedTester: ProviderSpeedTester
    private lateinit var providerScoreCache: ProviderScoreCache
    private lateinit var streamHealthMonitor: StreamHealthMonitor

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Log::class)
        mockkStatic("ua.ukrtv.app.ui.player.SeasonSerializerKt")
        every { serializeSeasons(any()) } returns "[]"
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
        watchProgressRepository = spyk(WatchProgressRepository(mockContext, mockDao))
        coEvery { watchProgressRepository.getStreamCacheForIds(any()) } returns emptyMap()
        
        streamResolver = mockk(relaxed = true)
        playerPreferences = mockk(relaxed = true)
        audioEngine = AudioEngine()
        thermalMonitor = mockk(relaxed = true)
        every { thermalMonitor.thermalStatus } returns emptyFlow()
        providerManager = mockk(relaxed = true)
        playerFactory = mockk(relaxed = true)
        playerPool = mockk(relaxed = true)
        mediaPrefetcher = mockk(relaxed = true)
        providerSpeedTester = mockk(relaxed = true)
        providerScoreCache = mockk(relaxed = true)
        streamHealthMonitor = mockk(relaxed = true)
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
            playerPool = playerPool,
            audioEngine = audioEngine,
            providerManager = providerManager,
            playerPreferences = playerPreferences,
            mediaPrefetcher = mediaPrefetcher,
            providerSpeedTester = providerSpeedTester,
            providerScoreCache = providerScoreCache,
            streamHealthMonitor = streamHealthMonitor,
            thermalMonitor = thermalMonitor
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

    @Test
    fun `prepareNextEpisode advances S1E1 to S1E2`() {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2), ep(3))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        assertTrue(vm.prepareNextEpisode())
        assertEquals(1, getField(vm, "preparedSeason"))
        assertEquals(2, getField(vm, "preparedEpisode"))
    }

    @Test
    fun `handleExternalPlayerResult full episode with seasons returns Advanced`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)

        val intent = mockk<Intent>()
        mockkConstructor(ExternalPlayerLauncher::class)
        every { anyConstructed<ExternalPlayerLauncher>().extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
    }

    @Test
    fun `createExternalPlayerIntent builds playlist with cached URLs`() = runTest {
        val vm = createViewModel()
        val season1 = season(1, ep(1), ep(2))
        setField(vm, "seasons", listOf(season1))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "contentId", "movie123")
        
        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://test/1/stream", title = "Movie", subtitle = "",
                positionMs = 0L, referer = "", streamType = StreamType.MP4
            )
        )

        val cachedUrl = "https://test/2/cached_stream"
        coEvery { watchProgressRepository.getStreamCacheForIds(any()) } returns mapOf(
            "movie123_s1e2" to WatchProgressRepository.StreamCache(cachedUrl, "MP4", "", emptyList())
        )
        
        every { playerPreferences.externalPlayerPackage.value } returns "com.mxtech.videoplayer.ad"
        mockkConstructor(ExternalPlayerLauncher::class)
        val configSlot = slot<ExternalPlayerLauncher.PlayerLaunchConfig>()
        every { anyConstructed<ExternalPlayerLauncher>().buildIntent(any(), capture(configSlot)) } returns mockk()

        vm.createExternalPlayerIntent()

        assertTrue(configSlot.isCaptured)
        val playlist = configSlot.captured.playlist
        assertEquals(2, playlist.size)
        assertEquals("https://test/1/stream", playlist[0].url) 
        assertEquals(cachedUrl, playlist[1].url) 
    }

    @Test
    fun `saveProgress saves when position changes`() {
        val vm = createViewModel()
        setField(vm, "contentId", "test_content")
        setField(vm, "episodeId", "s1e1")
        setField(vm, "title", "Test")
        setField(vm, "pageUrl", "https://test.com")
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
}
