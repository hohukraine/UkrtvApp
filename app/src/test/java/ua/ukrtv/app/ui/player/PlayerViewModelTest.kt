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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
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
import ua.ukrtv.app.domain.model.serializeSeasons
import ua.ukrtv.app.player.ExternalPlayerInfo
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.player.HttpDowngradeProbe
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
    private val createdViewModels = mutableListOf<PlayerViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Log::class)
        mockkStatic("ua.ukrtv.app.domain.model.SeasonSerializerKt")
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
        createdViewModels.forEach { vm ->
            runBlocking {
                vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            }
        }
        createdViewModels.clear()
        testDispatcher.scheduler.advanceUntilIdle()
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
            hlsPlaylistDuration = mockk(relaxed = true),
            httpDowngradeProbe = mockk(relaxed = true)
        ).also { createdViewModels.add(it) }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `handleExternalPlayerResult without launcher returns NoData`() = runTest {
        val vm = createViewModel()
        val result = vm.handleExternalPlayerResult(-1, mockk())
        assertEquals(ExternalPlayerReturnResult.NoData, result)
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `handleExternalPlayerResult with null result returns NoData`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns null
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(-1, mockk())
        assertEquals(ExternalPlayerReturnResult.NoData, result)
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `handleExternalPlayerResult with valid result returns NotFinished`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(60000L, 60000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(-1, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.NotFinished(60000L, 60000L), result)
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `createExternalPlayerIntent downgrades https to http for VLC when probe allows`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        setField(vm, "externalPlayerLauncher", mockLauncher)
        setField(vm, "installedPlayers", listOf(ExternalPlayerInfo.VLC))
        setField(vm, "contentId", "movie123")

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://ashdi.vip/stream/index.m3u8", title = "Movie", subtitle = "",
                positionMs = 0L, durationMs = 0L, referer = "https://ashdi.vip/vod/1", streamType = StreamType.HLS
            )
        )

        every { playerPreferences.externalPlayerPackage.value } returns "org.videolan.vlc"

        val probe = getField(vm, "httpDowngradeProbe") as HttpDowngradeProbe
        coEvery { probe.maybeDowngrade("https://ashdi.vip/stream/index.m3u8", StreamType.HLS, "https://ashdi.vip/vod/1") } returns "http://ashdi.vip/stream/index.m3u8"

        val configSlot = slot<ExternalPlayerLauncher.PlayerLaunchConfig>()
        val mockIntent = mockk<Intent>()
        every { mockLauncher.buildIntent(any(), capture(configSlot)) } returns mockIntent

        val result = vm.createExternalPlayerIntent()
        assertEquals(mockIntent, result)
        assertEquals("http://ashdi.vip/stream/index.m3u8", configSlot.captured.streamUrl)
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `createExternalPlayerIntent keeps https url for non-VLC players`() = runTest {
        val vm = createViewModel()
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        setField(vm, "externalPlayerLauncher", mockLauncher)
        setField(vm, "installedPlayers", listOf(ExternalPlayerInfo.VLC, ExternalPlayerInfo.JUST_PLAYER))
        setField(vm, "contentId", "movie123")

        val statusField = PlayerViewModel::class.java.getDeclaredField("_state")
        statusField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = statusField.get(vm) as MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(
            status = PlayerStatus.Ready(
                url = "https://ashdi.vip/stream/index.m3u8", title = "Movie", subtitle = "",
                positionMs = 0L, durationMs = 0L, referer = "", streamType = StreamType.HLS
            )
        )

        every { playerPreferences.externalPlayerPackage.value } returns ExternalPlayerInfo.JUST_PLAYER.packageName

        val probe = getField(vm, "httpDowngradeProbe") as HttpDowngradeProbe
        val configSlot = slot<ExternalPlayerLauncher.PlayerLaunchConfig>()
        every { mockLauncher.buildIntent(any(), capture(configSlot)) } returns mockk()

        vm.createExternalPlayerIntent()
        coVerify(exactly = 0) { probe.maybeDowngrade(any(), any(), any()) }
        assertEquals("https://ashdi.vip/stream/index.m3u8", configSlot.captured.streamUrl)
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `safeStreamType falls back to URL-based inference for unknown value`() {
        val vm = createViewModel()
        assertEquals(StreamType.HLS, vm.safeStreamType("DASH", "https://cdn.example.com/stream.m3u8"))
        assertEquals(StreamType.HLS, vm.safeStreamType("hls", "https://cdn.example.com/stream.m3u8"))
        assertEquals(StreamType.MPD, vm.safeStreamType("WEIRD", "https://cdn.example.com/stream.mpd"))
        assertEquals(StreamType.MP4, vm.safeStreamType("", "https://cdn.example.com/video.mp4"))
        assertEquals(StreamType.MP4, vm.safeStreamType("MP4", "https://cdn.example.com/video.mp4"))
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `result with position but unknown duration is saved without duration`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")
        setField(vm, "contentId", "content123")

        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(600_000L, 0L, false)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(ExternalPlayerReturnResult.NotFinished(600_000L, 0L), result)
        coVerify {
            watchProgressRepository.saveProgress(
                "content123", "s1e1", 600_000L, 0L, any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `position is never persisted as duration`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")
        setField(vm, "contentId", "content123")

        // Just Player reports a position but a TIME_UNSET duration for HLS/DASH.
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(1_000_000L, 0L, false)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        coVerify(exactly = 1) {
            watchProgressRepository.saveProgress(
                "content123", "s1e1", 1_000_000L, 0L, any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `empty result with natural completion and no duration does not fabricate a full entry`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")
        setField(vm, "contentId", "content123")

        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(0L, 0L, false)
        setField(vm, "externalPlayerLauncher", mockLauncher)
        setField(vm, "externalPlayerLaunchTimeMs", System.currentTimeMillis() - 120_000L)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        // savedDur is 0 and there is no persisted duration, so nothing may be persisted as a
        // fake 100% entry; the series just advances to the next episode.
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
        coVerify(exactly = 0) {
            watchProgressRepository.saveProgress("content123", "s1e1", any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `cancelled result near the end does not advance and saves real position`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "contentId", "content123")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")

        // User pressed back in VLC at 95%: RESULT_CANCELED, but the player still reported data.
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(95_000L, 100_000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_CANCELED, mockk<Intent>())

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(ExternalPlayerReturnResult.NotFinished(95_000L, 100_000L), result)
        assertEquals(1, getField(vm, "episode"))
        assertEquals("s1e1", getField(vm, "episodeId"))
        coVerify(exactly = 1) {
            watchProgressRepository.saveProgress("content123", "s1e1", 95_000L, 100_000L, any(), any(), any(), any(), any(), any(), any(), any())
        }
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `ended manually result near the end does not advance`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "contentId", "content123")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")

        // VLC reports end_by=exit with a near-complete position but a successful result code.
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(119_000L, 120_000L, false, endedManually = true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(ExternalPlayerReturnResult.NotFinished(119_000L, 120_000L), result)
        assertEquals("s1e1", getField(vm, "episodeId"))
        coVerify(exactly = 1) {
            watchProgressRepository.saveProgress("content123", "s1e1", 119_000L, 120_000L, any(), any(), any(), any(), any(), any(), any(), any())
        }
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `cancelAdvance re-saves finished episode at resumable position and restores navigation`() = runTest {
        val vm = createViewModel()
        setField(vm, "seasons", listOf(season(1, ep(1), ep(2))))
        setField(vm, "season", 1)
        setField(vm, "episode", 1)
        setField(vm, "episodeId", "s1e1")
        setField(vm, "contentId", "content123")
        setField(vm, "pageUrl", "https://test/series")
        setField(vm, "title", "Series")
        setField(vm, "poster", "poster.jpg")

        // Episode genuinely completes; the ViewModel advances to S1E2.
        val mockLauncher = mockk<ExternalPlayerLauncher>(relaxed = true)
        every { mockLauncher.extractResult(any(), any()) } returns ExternalPlayerLauncher.ExternalPlayerResult(100_000L, 100_000L, true)
        setField(vm, "externalPlayerLauncher", mockLauncher)

        val result = vm.handleExternalPlayerResult(Activity.RESULT_OK, mockk<Intent>())
        assertEquals(ExternalPlayerReturnResult.Advanced, result)
        assertEquals(2, getField(vm, "episode"))
        assertEquals("s1e2", getField(vm, "episodeId"))

        // User cancels the countdown: the finished episode must stay resumable.
        vm.cancelAdvance()

        kotlinx.coroutines.runBlocking {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        assertEquals(1, getField(vm, "season"))
        assertEquals(1, getField(vm, "episode"))
        assertEquals("s1e1", getField(vm, "episodeId"))
        coVerify(exactly = 1) {
            watchProgressRepository.saveProgress("content123", "s1e1", 95_000L, 100_000L, "Series", "poster.jpg", "https://test/series")
        }
        kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }
}
