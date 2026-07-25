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
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
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
import ua.ukrtv.app.player.ProviderQualityManager
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
    private lateinit var mediaPrefetcher: MediaPrefetcher
    private lateinit var providerQualityManager: ProviderQualityManager

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
        mediaPrefetcher = mockk(relaxed = true)
        providerQualityManager = mockk(relaxed = true)
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
            audioEngine = audioEngine,
            providerManager = providerManager,
            playerPreferences = playerPreferences,
            mediaPrefetcher = mediaPrefetcher,
            providerQualityManager = providerQualityManager,
            thermalMonitor = thermalMonitor,
            streamResolvingInteractor = mockk(relaxed = true),
            externalPlayerInteractor = mockk(relaxed = true)
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
    fun `handleExternalPlayerResult finished last episode returns NotFinished`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 2)
        setField(vm, "contentId", "movie123")
        setField(vm, "episodeId", "s1e2")

        val intent = mockk<Intent>()
        val interactor = getField(vm, "externalPlayerInteractor") as ExternalPlayerInteractor
        every { interactor.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.NotFinished(60000L, 60000L), result)
        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult finished non-last episode returns Advanced`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "contentId", "movie123")
        setField(vm, "episodeId", "s1e1")

        val intent = mockk<Intent>()
        val interactor = getField(vm, "externalPlayerInteractor") as ExternalPlayerInteractor
        every { interactor.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)

        val result = vm.handleExternalPlayerResult(-1, intent)
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult null result returns NoData without saved duration`() = runTest {
        val vm = createViewModel()
        setField(vm, "contentId", "movie123")
        setField(vm, "episodeId", "s1e1")
        setField(vm, "lastSavedPosition", 0L)
        
        val intent = mockk<Intent>()
        val interactor = getField(vm, "externalPlayerInteractor") as ExternalPlayerInteractor
        every { interactor.extractResult(any(), any()) } returns null

        val result = vm.handleExternalPlayerResult(-1, intent)
        
        assertEquals(ExternalPlayerReturnResult.NoData, result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult zero position and duration without saved duration returns NoData`() = runTest {
        val vm = createViewModel()
        setField(vm, "contentId", "movie123")
        setField(vm, "episodeId", "s1e1")
        
        val intent = mockk<Intent>()
        val interactor = getField(vm, "externalPlayerInteractor") as ExternalPlayerInteractor
        every { interactor.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(0L, 0L, false)

        val result = vm.handleExternalPlayerResult(-1, intent)
        
        assertEquals(ExternalPlayerReturnResult.NoData, result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `createExternalPlayerIntent returns non-null when status is Ready`() = runTest {
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
        
        every { playerPreferences.externalPlayerPackage.value } returns "org.videolan.vlc"
        val mockIntent = mockk<Intent>()
        val interactor = getField(vm, "externalPlayerInteractor") as ExternalPlayerInteractor
        coEvery { interactor.buildIntent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns mockIntent

        val result = vm.createExternalPlayerIntent()
        assertNotNull(result)
        assertEquals(mockIntent, result)

        coVerify {
            interactor.buildIntent(
                contentId = "movie123",
                title = "Movie",
                url = "https://test/1/stream",
                streamType = StreamType.MP4,
                referer = any(),
                positionMs = 0L,
                durationMs = 0L,
                season = 1,
                episode = 1,
                voiceover = any(),
                seasons = listOf(season1)
            )
        }
        vm.viewModelScope.cancel()
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
        vm.viewModelScope.cancel()
    }
}
