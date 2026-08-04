package ua.ukrtv.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import ua.ukrtv.app.player.ExternalPlayerInfo
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PlayerPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var savedStateHandle: androidx.lifecycle.SavedStateHandle
    private lateinit var watchProgressRepository: WatchProgressRepository
    private lateinit var streamResolver: StreamResolver
    private lateinit var playerPreferences: PlayerPreferences
    private lateinit var providerManager: ProviderManager

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
        providerManager = mockk(relaxed = true)
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
            streamResolver = streamResolver,
            providerManager = providerManager,
            playerPreferences = playerPreferences,
            streamResolvingInteractor = mockk(relaxed = true),
            hlsPlaylistDuration = mockk(relaxed = true)
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
    fun `finished playback advances from S1E1 to S1E2 and resolves its stream`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2)), season(2, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")

        val interactor = getField(vm, "streamResolvingInteractor") as StreamResolvingInteractor
        val argSeason = slot<Int>()
        val argEpisode = slot<Int>()
        coEvery { interactor.resolve(any(), any(), capture(argSeason), capture(argEpisode), any(), any()) } returns StreamResolutionResult(
            streamUrl = "https://cdn/s1e2.m3u8",
            streamType = StreamType.HLS,
            referer = "https://test/"
        )
        coEvery { watchProgressRepository.getStreamCache(any(), any()) } returns null

        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.Advanced, result)

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(1, getField(vm, "season"))
        assertEquals(2, getField(vm, "episode"))
        assertEquals("s1e2", getField(vm, "episodeId"))
        assertEquals(1, argSeason.captured)
        assertEquals(2, argEpisode.captured)

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        assertEquals(1, stateFlow.value.currentSeason)
        assertEquals(2, stateFlow.value.currentEpisode)
        val ready = stateFlow.value.status as? PlayerStatus.Ready
        assertEquals("https://cdn/s1e2.m3u8", ready?.url)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `auto-advance ignores invalid cached stream url and resolves the real stream`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "contentId", "content123")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")

        coEvery { watchProgressRepository.getStreamCache(any(), "s1e2") } returns WatchProgressRepository.StreamCache(
            streamUrl = "https://test/series", streamType = "MP4", referer = "", fallbackUrls = emptyList(), durationMs = 0L
        )

        val interactor = getField(vm, "streamResolvingInteractor") as StreamResolvingInteractor
        val argEpisode = slot<Int>()
        coEvery { interactor.resolve(any(), any(), any(), capture(argEpisode), any(), any()) } returns StreamResolutionResult(
            streamUrl = "https://cdn/s1e2.m3u8", streamType = StreamType.HLS, referer = "https://test/"
        )

        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.Advanced, result)

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(2, argEpisode.captured)
        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        val ready = stateFlow.value.status as? PlayerStatus.Ready
        assertEquals("https://cdn/s1e2.m3u8", ready?.url)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `preResolveNextEpisode persists the real stream url for the next episode`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2), ep(3))))
        setField(vm, "season", 1)
        setField(vm, "episode", 2)
        setField(vm, "episodeId", "s1e2")
        setField(vm, "contentId", "content123")
        setField(vm, "pageUrl", "https://test/series")

        val rawResolver = getField(vm, "streamResolver") as StreamResolver
        coEvery { rawResolver.resolve(any(), any(), any(), any(), any(), any()) } returns StreamResolutionResult(
            streamUrl = "https://cdn/s1e3.m3u8", streamType = StreamType.HLS, referer = "https://test/"
        )

        val method = PlayerViewModel::class.java.getDeclaredMethod("preResolveNextEpisode")
        method.isAccessible = true
        method.invoke(vm)

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        coVerify {
            watchProgressRepository.saveProgress(
                any(), "s1e3", any(), any(), any(), any(), any(), "https://cdn/s1e3.m3u8", "HLS", any(), any()
            )
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `finished playback on last episode does not advance`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1)), season(2, ep(1), ep(2))))
        setField(vm, "season", 2)
        setField(vm, "episode", 2)
        setField(vm, "episodeId", "s2e2")
        setField(vm, "pageUrl", "https://test/series")

        val interactor = getField(vm, "streamResolvingInteractor") as StreamResolvingInteractor
        val argEpisode = slot<Int>()
        coEvery { interactor.resolve(any(), any(), any(), capture(argEpisode), any(), any()) } returns null

        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.NotFinished(60000L, 60000L), result)
        assertEquals(2, getField(vm, "episode"))
        assertEquals("s2e2", getField(vm, "episodeId"))
        assertFalse(argEpisode.isCaptured)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult without launcher returns NoData`() = runTest {
        val vm = createViewModel()
        val result = vm.handleExternalPlayerResult(-1, mockk())
        assertEquals(ExternalPlayerReturnResult.NoData, result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult with null result returns NoData`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns null
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(-1, mockk())
        assertEquals(ExternalPlayerReturnResult.NoData, result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult with valid result returns NotFinished`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(-1, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.NotFinished(60000L, 60000L), result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult with no duration and long playback advances`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(1_000_000L, 0L, false)
        setField(vm, "externalPlayerLauncher", mockLauncher)
        setField(vm, "externalPlayerLaunchTimeMs", System.currentTimeMillis() - 120_000L)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.Advanced, result)

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `handleExternalPlayerResult with no duration and short playback does not advance`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60_000L, 0L, false)
        setField(vm, "externalPlayerLauncher", mockLauncher)
        setField(vm, "externalPlayerLaunchTimeMs", System.currentTimeMillis() - 10_000L)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.NotFinished(60_000L, 0L), result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `createExternalPlayerIntent returns non-null when status is Ready`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        setField(vm, "externalPlayerLauncher", mockLauncher)
        setField(vm, "installedPlayers", listOf(ExternalPlayerInfo.VLC))
        setField(vm, "contentId", "movie123")

        every { mockLauncher.buildIntent(any(), any()) } returns mockk()

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://test/1/stream", title = "Movie", subtitle = "",
                positionMs = 0L, durationMs = 0L, referer = "", streamType = StreamType.MP4
            )
        )

        every { playerPreferences.externalPlayerPackage.value } returns "org.videolan.vlc"

        val result = vm.createExternalPlayerIntent()
        assertNotNull(result)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `safeStreamType falls back to URL-based inference for unknown value`() {
        val vm = createViewModel()
        assertEquals(StreamType.HLS, vm.safeStreamType("DASH", "https://cdn.example.com/stream.m3u8"))
        assertEquals(StreamType.HLS, vm.safeStreamType("hls", "https://cdn.example.com/stream.m3u8"))
        assertEquals(StreamType.MPD, vm.safeStreamType("WEIRD", "https://cdn.example.com/stream.mpd"))
        assertEquals(StreamType.MP4, vm.safeStreamType("", "https://cdn.example.com/video.mp4"))
        assertEquals(StreamType.MP4, vm.safeStreamType("MP4", "https://cdn.example.com/video.mp4"))
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
                positionMs = 6000L, durationMs = 60000L, referer = "", streamType = StreamType.HLS
            )
        )

        vm.saveProgress(6000L, 60000L)

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(6000L, getField(vm, "lastSavedPosition"))
        vm.viewModelScope.cancel()
    }
}
