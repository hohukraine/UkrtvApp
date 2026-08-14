package ua.ukrtv.app.ui.player

import android.app.Activity
import android.content.Context
import ua.ukrtv.app.util.AppLogger
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.HlsPlaylistDuration
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.deserializeSeasons
import ua.ukrtv.app.domain.model.serializeSeasons
import ua.ukrtv.app.player.ExternalPlayerInfo
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.player.HttpDowngradeProbe
import ua.ukrtv.app.ui.player.PlayerStatus
import ua.ukrtv.app.util.PlayerPreferences
import ua.ukrtv.app.domain.model.StreamType
import android.content.Intent
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext val appContext: Context,
    val savedStateHandle: SavedStateHandle,
    private val watchProgressRepository: WatchProgressRepository,
    private val streamResolver: StreamResolver,
    private val providerManager: ProviderManager,
    val playerPreferences: PlayerPreferences,
    private val streamResolvingInteractor: StreamResolvingInteractor,
    private val hlsPlaylistDuration: HlsPlaylistDuration,
    private val httpDowngradeProbe: HttpDowngradeProbe
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _deepResolutionCompleted = MutableStateFlow(false)
    val deepResolutionCompleted: StateFlow<Boolean> = _deepResolutionCompleted.asStateFlow()

    private var contentId: String = ""
    private var title: String = ""
    private var pageUrl: String = ""
    private var poster: String = ""
    private var referer: String = ""
    private var subtitle: String = ""
    private var season: Int? = null
    private var episode: Int? = null
    private var episodeId: String? = null
    private var voiceover: String? = null
    private var retryCount: Int = 0
    private var crossProviderRetried = false
    private var availableStreams: MutableList<String> = mutableListOf()
    private var currentStreamIndex: Int = 0
    private var seasons: List<Season> = emptyList()

    private var loadJob: kotlinx.coroutines.Job? = null
    private var deepJob: kotlinx.coroutines.Job? = null
    private var preResolveJob: kotlinx.coroutines.Job? = null
    private var isResolving = false
    private var isAutoAdvancing = false
    private var externalPlayerLaunchTimeMs: Long = 0L

    private var lastFinishedSeason: Int? = null
    private var lastFinishedEpisode: Int? = null
    private var lastFinishedEpisodeId: String? = null
    private var lastFinishedPositionMs: Long = 0L
    private var lastFinishedDurationMs: Long = 0L

    private fun inferSeasonEpisodeFromUrl(url: String) {
        if (this.season != null && this.episode != null) return
        
        val sNum = ua.ukrtv.app.data.providers.DleResolutionUtils.extractSeasonNum(url)
        if (this.season == null && sNum != null) {
            this.season = sNum
            savedStateHandle[KEY_SEASON] = sNum
            AppLogger.d("ExternalPlayer", "Inferred season $sNum from URL")
        }
        
        // Simple episode inference from URL if possible (e.g. s01e05, episode-08, /5-seriya).
        // Deliberately conservative: the URL's numeric content ID must never be read as an episode.
        if (this.episode == null) {
            val eNum = ua.ukrtv.app.data.providers.DleResolutionUtils.extractEpisodeNum(url)
            if (eNum != null) {
                this.episode = eNum
                savedStateHandle[KEY_EPISODE] = eNum
                AppLogger.d("ExternalPlayer", "Inferred episode $eNum from URL")
            } else if (this.season != null) {
                // If we have a season but no episode, it's often the first one
                this.episode = 1
                savedStateHandle[KEY_EPISODE] = 1
                AppLogger.d("ExternalPlayer", "Defaulted episode to 1 for season ${this.season}")
            }
        }
        this.episodeId = if (this.season != null && this.episode != null) "s${this.season}e${this.episode}" else null
    }

    internal fun safeStreamType(name: String, url: String): StreamType =
        StreamType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: ua.ukrtv.app.data.streaming.getStreamType(url)

    private val externalPlayerLaunchLock = Any()
    private var externalPlayerLaunchLocked = false
    private var externalPlayerLauncher: ExternalPlayerLauncher? = null
    private var installedPlayers: List<ExternalPlayerInfo> = emptyList()

    fun tryAcquireExternalPlayerLaunchLock(): Boolean {
        synchronized(externalPlayerLaunchLock) {
            if (externalPlayerLaunchLocked) return false
            externalPlayerLaunchLocked = true
            return true
        }
    }

    fun releaseExternalPlayerLaunchLock() {
        externalPlayerLaunchLocked = false
    }

    private var pendingSeason: Int? = null
    private var pendingEpisode: Int? = null
    private var pendingVoiceover: String? = null

    fun initialize(contentId: String, title: String, pageUrl: String, season: Int? = null, episode: Int? = null, poster: String = "") {
        val savedSeason = savedStateHandle.get<Int>(KEY_SEASON)
        val savedEpisode = savedStateHandle.get<Int>(KEY_EPISODE)
        val savedContentId = savedStateHandle.get<String>(KEY_CONTENT_ID)

        // Prefer saved state if it's the same content (survives auto-advance and process death)
        val effectiveSeason = if (savedContentId == contentId && savedSeason != null) savedSeason else season
        val effectiveEpisode = if (savedContentId == contentId && savedEpisode != null) savedEpisode else episode

        val currentEpisodeId = if (effectiveSeason != null && effectiveEpisode != null) "s${effectiveSeason}e${effectiveEpisode}" else null
        val oldEpisodeId = savedStateHandle.get<String>("last_episode_id")

        if (savedContentId != contentId || oldEpisodeId != currentEpisodeId) {
            savedStateHandle[KEY_PENDING_RESULT] = false
            savedStateHandle[KEY_EXTERNAL_DURATION] = 0L
        }
        
        savedStateHandle[KEY_CONTENT_ID] = contentId
        savedStateHandle["last_episode_id"] = currentEpisodeId
        savedStateHandle[KEY_SEASON] = effectiveSeason
        savedStateHandle[KEY_EPISODE] = effectiveEpisode
        
        if (_state.value.status is PlayerStatus.Ready || _state.value.status is PlayerStatus.Loading) {
            if (this.contentId == contentId && this.season == effectiveSeason && this.episode == effectiveEpisode) return
        }
        
        this.contentId = contentId
        this.title = title
        this.pageUrl = pageUrl
        this.poster = poster
        this.season = effectiveSeason
        this.episode = effectiveEpisode
        this.episodeId = currentEpisodeId

        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.getSeasonsJson(contentId, episodeId)?.let { json ->
                deserializeSeasons(json).takeIf { it.isNotEmpty() }?.let { restored ->
                    withContext(Dispatchers.Main) {
                        seasons = restored
                        updateNavigationState()
                        rebuildPickerColumns()
                    }
                }
            }
        }

        savedStateHandle[KEY_CONTENT_ID] = contentId
        savedStateHandle[KEY_PAGE_URL] = pageUrl
        savedStateHandle[KEY_SEASON] = effectiveSeason
        savedStateHandle[KEY_EPISODE] = effectiveEpisode
        savedStateHandle[KEY_TITLE] = title
        savedStateHandle[KEY_POSTER] = poster
        savedStateHandle[KEY_VOICEOVER] = this.voiceover
        
        inferSeasonEpisodeFromUrl(pageUrl)
        
        updateNavigationState()
        initPickerColumns()
        loadStream(pageUrl, if (this.season != null && this.episode != null) "S${this.season} E${this.episode}" else "", null)
    }

    private fun loadStream(url: String, subtitle: String, forceStartPosition: Long?) {
        deepJob?.cancel()
        loadJob?.cancel()
        preResolveJob?.cancel()
        lastSavedPosition = -1L
        isResolving = true

        _state.update { it.copy(status = PlayerStatus.Loading(this.title), deepResolutionPending = true) }
        loadJob = viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { watchProgressRepository.getStreamCache(contentId, episodeId) }
                if (cached != null && ua.ukrtv.app.data.streaming.isDirectStreamUrl(cached.streamUrl)) {
                    this@PlayerViewModel.availableStreams = (listOf(cached.streamUrl) + cached.fallbackUrls).distinct().toMutableList()
                    this@PlayerViewModel.referer = cached.referer
                    val pos = forceStartPosition ?: withContext(Dispatchers.IO) { watchProgressRepository.getProgress(contentId, episodeId)?.positionMs } ?: 0L
                    val displayTitle = if (subtitle.isNotEmpty()) "${this@PlayerViewModel.title} ($subtitle)" else this@PlayerViewModel.title
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(cached.streamUrl, displayTitle, subtitle, pos, cached.durationMs, cached.referer, safeStreamType(cached.streamType, cached.streamUrl)),
                        availableSeasons = this@PlayerViewModel.seasons
                    ) }
                    updateNavigationState()
                    isResolving = false
                    launchDeepResolution()
                    preResolveNextEpisode()
                    return@launch
                }

                val res = streamResolvingInteractor.resolve(url, this@PlayerViewModel.title, this@PlayerViewModel.season, this@PlayerViewModel.episode, this@PlayerViewModel.voiceover, false)
                if (res != null) {
                    applyStreamResult(res, subtitle, forceStartPosition ?: getSavedPosition())
                    preResolveNextEpisode()
                } else {
                    _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) _state.update { it.copy(status = PlayerStatus.Error(e.message ?: "Error")) }
            } finally {
                isResolving = false
                isAutoAdvancing = false
            }
        }
    }

    private fun applyStreamResult(res: ua.ukrtv.app.domain.model.StreamResolutionResult, subtitle: String, pos: Long) {
        availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
        referer = res.referer
        
        // Merge seasons instead of overwriting
        val newSeasons = res.seasons
        if (newSeasons != null && newSeasons.isNotEmpty()) {
            if (this.seasons.isEmpty()) {
                this.seasons = newSeasons
            } else {
                // Keep existing seasons but update/add new ones
                val merged = (this.seasons + newSeasons).distinctBy { it.number }.sortedBy { it.number }
                this.seasons = merged
            }
        }

        if ((this.season == null || this.episode == null) && this.seasons.isNotEmpty()) {
            if (this.season == null) this.season = this.seasons.first().number
            if (this.episode == null && this.seasons.first().episodes.isNotEmpty()) {
                this.episode = this.seasons.first().episodes.first().number
            }
            this.episodeId = if (this.season != null && this.episode != null) "s${this.season}e${this.episode}" else null
            savedStateHandle[KEY_SEASON] = this.season
            savedStateHandle[KEY_EPISODE] = this.episode
        }

        // Infer if still missing after resolution
        inferSeasonEpisodeFromUrl(res.sourcePageUrl.ifEmpty { pageUrl })
        
        val displayTitle = if (subtitle.isEmpty() && this.season != null) {
             "${this.title} (S${this.season} E${this.episode})"
        } else if (subtitle.isNotEmpty()) {
            "${this.title} ($subtitle)"
        } else this.title

        _state.update { it.copy(
            status = PlayerStatus.Ready(res.streamUrl, displayTitle, subtitle, pos, 0L, res.referer, res.streamType),
            availableSeasons = seasons
        ) }
        updateNavigationState()
        launchDeepResolution()
    }

    private fun launchDeepResolution() {
        deepJob?.cancel()
        _deepResolutionCompleted.value = false
        deepJob = viewModelScope.launch {
            try {
                val deepRes = withContext(Dispatchers.IO) { streamResolver.resolve(this@PlayerViewModel.pageUrl, isDeep = true) }
                val newSeasons = deepRes?.seasons
                if (newSeasons != null && newSeasons.isNotEmpty()) {
                    this@PlayerViewModel.seasons = newSeasons
                    _state.update { it.copy(availableSeasons = newSeasons, deepResolutionPending = false) }
                    rebuildPickerColumns()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Deep resolution is best-effort (episode list enrichment). Never let a network
                // or provider exception escape the viewModelScope — it would crash the app right
                // during the auto-advance to the next episode.
                AppLogger.w("ExternalPlayer", "Deep resolution failed: ${e.message}")
            } finally {
                _state.update { it.copy(deepResolutionPending = false) }
                _deepResolutionCompleted.value = true
            }
        }
    }

    private fun preResolveNextEpisode() {
        preResolveJob?.cancel()
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return
        preResolveJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    streamResolver.resolve(
                        url = pageUrl,
                        season = nav.season,
                        episode = nav.episode,
                        voiceover = voiceover,
                        isDeep = false
                    )
                }
                if (result != null) {
                    withContext(Dispatchers.IO) {
                        watchProgressRepository.saveProgress(
                            contentId, "s${nav.season}e${nav.episode}", 0L, 0L, "", "",
                            pageUrl, result.streamUrl, result.streamType.name, result.referer, result.fallbackStreams.joinToString("|")
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun updateNavigationState() {
        _state.update { it.copy(currentSeason = this.season, currentEpisode = this.episode, currentVoiceover = this.voiceover, availableSeasons = this.seasons) }
        rebuildPickerColumns()
    }

    private suspend fun getSavedPosition(): Long = withContext(Dispatchers.IO) { watchProgressRepository.getProgress(contentId, episodeId)?.positionMs ?: 0L }

    private var lastSavedPosition: Long = -1L

    private suspend fun saveProgressSynchronously(pos: Long, dur: Long) {
        if (dur <= 0 && pos <= 0) return
        if (pos == lastSavedPosition && pos > 0L) return
        lastSavedPosition = pos

        // Capture current state to avoid race conditions during episode transitions
        val currentContentId = this.contentId
        val currentEpisodeId = this.episodeId
        val currentTitle = this.title
        val currentPoster = this.poster
        val currentPageUrl = this.pageUrl
        val currentReferer = this.referer
        val currentSeasons = this.seasons.toList()
        val currentStreams = this.availableStreams.toList()
        val status = _state.value.status as? PlayerStatus.Ready

        val seasonsJson = if (currentSeasons.isNotEmpty()) serializeSeasons(currentSeasons) else null
        val fallbackUrls = currentStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")

        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            watchProgressRepository.saveProgress(
                currentContentId, currentEpisodeId, pos, dur, currentTitle, currentPoster, currentPageUrl,
                status?.url, status?.streamType?.name, currentReferer, fallbackUrls, seasonsJson = seasonsJson
            )
        }
    }

    fun saveProgress(pos: Long, dur: Long) {
        viewModelScope.launch {
            saveProgressSynchronously(pos, dur)
        }
    }

    fun retry() { loadStream(pageUrl, this.subtitle, null) }

    fun prepareNextEpisode(): Boolean {
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return false
        preparedSeason = nav.season
        preparedEpisode = nav.episode
        return true
    }

    /**
     * Best-effort fetch of the season/episode list when it hasn't arrived yet (deep resolution
     * still pending or failed). Used before falling back to exiting when an episode ends.
     */
    suspend fun ensureSeasons(): Boolean {
        if (seasons.isNotEmpty()) return true
        val resolved = withContext(Dispatchers.IO) {
            runCatching { streamResolver.resolve(pageUrl, isDeep = true) }.getOrNull()
        }
        val newSeasons = resolved?.seasons
        if (newSeasons == null || newSeasons.isEmpty()) return false
        this.seasons = newSeasons
        _state.update { it.copy(availableSeasons = newSeasons, deepResolutionPending = false) }
        rebuildPickerColumns()
        return true
    }

    fun executePreparedNavigation() {
        val s = preparedSeason ?: return
        val e = preparedEpisode ?: return
        if (isAutoAdvancing) return
        isAutoAdvancing = true
        preparedSeason = null; preparedEpisode = null
        this.season = s; this.episode = e; this.episodeId = "s${s}e${e}"
        pendingSeason = s; pendingEpisode = e
        savedStateHandle[KEY_SEASON] = s; savedStateHandle[KEY_EPISODE] = e
        loadStream(pageUrl, "S$s E$e", null)
        updateNavigationState()
    }

    fun navigateToPreviousEpisode(): Boolean {
        val nav = EpisodeNavigator.previousEpisode(seasons, season, episode) ?: return false
        applyEpisodeNavigation(nav.season, nav.episode)
        return true
    }

    private fun applyEpisodeNavigation(season: Int, episode: Int) {
        this.season = season; this.episode = episode; this.episodeId = "s${season}e${episode}"
        pendingSeason = season; pendingEpisode = episode
        loadStream(pageUrl, "S$season E$episode", null)
        updateNavigationState()
    }

    fun onEpisodeSelected(s: Int, e: Int, voiceover: String?) {
        this.voiceover = voiceover ?: this.voiceover
        applyEpisodeNavigation(s, e)
    }

    fun hasNextEpisode(): Boolean {
        if (this.season == null || this.episode == null) {
            if (seasons.isNotEmpty() && (this.season == null || this.episode == null)) {
                val firstSeason = seasons.first()
                if (this.season == null) { this.season = firstSeason.number; savedStateHandle[KEY_SEASON] = firstSeason.number }
                if (this.episode == null && firstSeason.episodes.isNotEmpty()) {
                    this.episode = firstSeason.episodes.first().number
                    savedStateHandle[KEY_EPISODE] = this.episode
                }
            } else {
                inferSeasonEpisodeFromUrl(pageUrl)
            }
        }
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode)
        AppLogger.d("ExternalPlayer", "hasNextEpisode check: current=S${season}E${episode}, seasonsCount=${seasons.size}, result=${nav != null}")
        return nav != null
    }
    fun hasPreviousEpisode(): Boolean = EpisodeNavigator.hasPreviousEpisode(seasons, season, episode)

    private var preparedSeason: Int? = null
    private var preparedEpisode: Int? = null

    fun getPreparedEpisode(): Pair<Int, Int>? = preparedSeason?.let { s -> preparedEpisode?.let { e -> s to e } }

    fun releaseEngine() {
        externalPlayerLauncher = null
        installedPlayers = emptyList()
    }

    fun hasPendingExternalPlayerResult(): Boolean {
        return savedStateHandle.get<Boolean>(KEY_PENDING_RESULT) ?: false
    }

    fun getCurrentExternalPlayerInfo(): ExternalPlayerInfo? {
        if (externalPlayerLauncher == null) {
            externalPlayerLauncher = ExternalPlayerLauncher(appContext)
            installedPlayers = externalPlayerLauncher!!.detectInstalledPlayers()
        }
        val pkg = playerPreferences.externalPlayerPackage.value
        val cached = installedPlayers.find { it.packageName == pkg }
        if (cached != null) return cached
        return externalPlayerLauncher!!.getPlayerInfo(pkg)
    }

    suspend fun createExternalPlayerIntent(): Intent? {
        val status = _state.value.status as? PlayerStatus.Ready ?: return null
        val launcher = externalPlayerLauncher
            ?: ExternalPlayerLauncher(appContext).also { externalPlayerLauncher = it }
        val playerInfo = getCurrentExternalPlayerInfo()
        if (playerInfo == null) {
            AppLogger.d("ExternalPlayer", "createExternalPlayerIntent: playerInfo is null, pkg=${playerPreferences.externalPlayerPackage.value}, installed=${installedPlayers.map { it.packageName }}")
            return null
        }
        // For VLC, untrusted-cert https streams are downgraded to plain http (when the host
        // serves the same content over http) so VLC never shows its certificate dialog.
        var streamUrl = status.url
        if (playerInfo.packageName == ExternalPlayerInfo.VLC.packageName) {
            streamUrl = httpDowngradeProbe.maybeDowngrade(streamUrl, status.streamType, status.referer)
        }
        val config = ExternalPlayerLauncher.PlayerLaunchConfig(
            streamUrl = streamUrl,
            streamType = status.streamType,
            title = status.title,
            referer = status.referer,
            positionMs = status.positionMs,
            durationMs = savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L
        )
        val intent = launcher.buildIntent(playerInfo, config)
        if (intent == null) {
            AppLogger.d("ExternalPlayer", "createExternalPlayerIntent: buildIntent returned null for ${playerInfo.packageName}")
        }
        return intent
    }

    suspend fun handleExternalPlayerResult(resultCode: Int, data: Intent?): ExternalPlayerReturnResult {
        savedStateHandle[KEY_PENDING_RESULT] = false
        val result = externalPlayerLauncher?.extractResult(resultCode, data)

        val savedDur = savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L

        val elapsedInPlayerMs = if (externalPlayerLaunchTimeMs > 0L) System.currentTimeMillis() - externalPlayerLaunchTimeMs else 0L

        // A cancelled activity result (user pressed back in the player) or an explicit
        // exit/stop report means the user walked away mid-playback. Their position must be
        // preserved as-is, never treated as a finished episode that auto-advances.
        val manualExit = resultCode == Activity.RESULT_CANCELED || result?.endedManually == true

        AppLogger.d("ExternalPlayer", "handleResult: result=$result, savedDur=$savedDur, elapsed=${elapsedInPlayerMs}ms, manualExit=$manualExit")

        // Fallback: players (VLC especially) sometimes return 0/0 with no end_by even after
        // natural completion. If the player reported RESULT_OK and the user spent a meaningful
        // time in it, treat the playback as completed.
        val looksLikeNaturalCompletion = !manualExit && resultCode == Activity.RESULT_OK &&
                elapsedInPlayerMs >= MIN_EXTERNAL_PLAYBACK_MS_FOR_COMPLETION

        // Player returned nothing usable (activity destroyed without a result, VLC released its
        // service before reporting, etc.). We have no position to persist — never fabricate one.
        // Only the elapsed-time heuristic may mark the content as finished and, for series,
        // advance to the next episode.
        if (result == null || (result.positionMs == 0L && result.durationMs == 0L && !result.isFinished)) {
            val effectiveDur = if (savedDur > 0) savedDur else {
                withContext(Dispatchers.IO) { watchProgressRepository.getProgress(contentId, episodeId)?.durationMs ?: 0L }
            }

            AppLogger.d("ExternalPlayer", "Result is empty, effectiveDur=$effectiveDur, looksLikeNaturalCompletion=$looksLikeNaturalCompletion")

            if (looksLikeNaturalCompletion) {
                // RESULT_OK + meaningful playback time: mark as finished so a completed
                // episode/movie leaves "Продовжити перегляд" instead of lingering.
                if (effectiveDur > 0) {
                    saveProgressSynchronously(effectiveDur, effectiveDur)
                }
                if (hasNextEpisode()) {
                    AppLogger.d("ExternalPlayer", "Advancing from empty result + natural completion")
                    rememberFinishedEpisode(effectiveDur, effectiveDur)
                    advanceToNextEpisodeFromExternalPlayer()
                    return ExternalPlayerReturnResult.Advanced
                }
            }
            return ExternalPlayerReturnResult.NoData
        }

        var durationMs = if (result.durationMs > 0) result.durationMs else savedDur

        // ExoPlayer-based players (Just Player) often omit duration for HLS/DASH streams
        // (player.getDuration() == TIME_UNSET), so a completion ratio can't be computed.
        // Try to resolve it from the manifest; if it stays unknown, keep durationMs == 0 and
        // save the raw position — "Продовжити перегляд" shows entries with meaningful progress
        // even when the duration is unknown.
        if (durationMs <= 0L && !result.isFinished && result.positionMs > 0L) {
            val readyStatus = _state.value.status as? PlayerStatus.Ready
            val resolved = readyStatus?.let { hlsPlaylistDuration.resolveDurationMs(it.url, it.referer) }
            if (resolved != null && resolved > 0L) {
                durationMs = resolved
                savedStateHandle.set<Long>(KEY_EXTERNAL_DURATION, resolved)
                AppLogger.d("ExternalPlayer", "Resolved duration at return: $resolved ms")
            } else if (looksLikeNaturalCompletion && hasNextEpisode()) {
                // Series: no duration but the user spent a meaningful time in the player.
                // Save the raw position with unknown duration (keeps the current episode in
                // "Продовжити перегляд") and advance to the next episode.
                AppLogger.d("ExternalPlayer", "No duration, RESULT_OK + ${elapsedInPlayerMs}ms -> advancing (series)")
                saveProgressSynchronously(result.positionMs, 0L)
                rememberFinishedEpisode(result.positionMs, 0L)
                advanceToNextEpisodeFromExternalPlayer()
                return ExternalPlayerReturnResult.Advanced
            }
        }

        val ratio = if (durationMs > 0 && result.positionMs > 0) result.positionMs.toFloat() / durationMs else 0f
        val isFinished = !manualExit && (
                result.isFinished ||
                (durationMs > 0 && result.positionMs > 0 && ratio >= 0.90f) ||
                (result.positionMs <= 0L && durationMs > 0 && looksLikeNaturalCompletion)
        )

        AppLogger.d("ExternalPlayer", "isFinished=$isFinished, pos=${result.positionMs}, dur=$durationMs, ratio=$ratio")

        val posToSave = when {
            isFinished && durationMs > 0 -> durationMs
            isFinished && result.positionMs > 0 -> result.positionMs
            else -> result.positionMs
        }

        if (posToSave > 0 || isFinished) {
            // Never substitute the position for an unknown duration: that would create a 100%
            // entry which is hidden from "Продовжити перегляд". durationMs is stored as-is
            // (0 means the duration is still unknown).
            saveProgressSynchronously(posToSave, durationMs)
        }

        if (isFinished && hasNextEpisode()) {
            AppLogger.d("ExternalPlayer", "Advancing to next episode")
            rememberFinishedEpisode(
                realPos = if (result.positionMs > 0L) result.positionMs else durationMs,
                dur = durationMs
            )
            advanceToNextEpisodeFromExternalPlayer()
            return ExternalPlayerReturnResult.Advanced
        }

        AppLogger.d("ExternalPlayer", "Returning NotFinished")
        return ExternalPlayerReturnResult.NotFinished(result.positionMs, durationMs)
    }

    /**
     * Captures the episode that just finished so that cancelling the auto-advance countdown
     * ([cancelAdvance]) can restore it as a resumable entry instead of leaving it at 100%.
     */
    private fun rememberFinishedEpisode(realPos: Long, dur: Long) {
        lastFinishedSeason = this.season
        lastFinishedEpisode = this.episode
        lastFinishedEpisodeId = this.episodeId
        lastFinishedPositionMs = realPos
        lastFinishedDurationMs = dur
    }

    /**
     * Called when the user cancels the "next episode" countdown. The finished episode must not
     * stay at 100% (it would vanish from "Продовжити перегляд"); re-save it at a resumable
     * position just below the completion threshold and revert the navigation state to it.
     */
    fun cancelAdvance() {
        loadJob?.cancel()
        preResolveJob?.cancel()
        val finishedEpisodeId = lastFinishedEpisodeId ?: return
        val resumePos = if (lastFinishedDurationMs > 0L) {
            minOf(lastFinishedPositionMs, (lastFinishedDurationMs * 0.95).toLong().coerceAtLeast(0L))
        } else lastFinishedPositionMs

        val contentId = this.contentId
        val title = this.title
        val poster = this.poster
        val pageUrl = this.pageUrl
        val durationMs = lastFinishedDurationMs
        viewModelScope.launch(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            // Stream fields are omitted so the repository keeps the finished episode's own
            // cached stream instead of the next episode's.
            watchProgressRepository.saveProgress(
                contentId, finishedEpisodeId, resumePos, durationMs, title, poster, pageUrl
            )
        }

        this.season = lastFinishedSeason
        this.episode = lastFinishedEpisode
        this.episodeId = finishedEpisodeId
        savedStateHandle[KEY_SEASON] = lastFinishedSeason
        savedStateHandle[KEY_EPISODE] = lastFinishedEpisode
        isAutoAdvancing = false
        updateNavigationState()
    }

    private fun advanceToNextEpisodeFromExternalPlayer() {
        if (isAutoAdvancing) return
        isAutoAdvancing = true
        
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: run {
            isAutoAdvancing = false
            return
        }
        
        // Preserve seasons for the next episode initialization
        val preservedSeasons = this.seasons
        
        this.season = nav.season
        this.episode = nav.episode
        this.episodeId = "s${nav.season}e${nav.episode}"
        savedStateHandle[KEY_SEASON] = nav.season
        savedStateHandle[KEY_EPISODE] = nav.episode
        
        loadStream(pageUrl, "S${nav.season} E${nav.episode}", null)
        
        if (this.seasons.isEmpty() && preservedSeasons.isNotEmpty()) {
            this.seasons = preservedSeasons
        }
        
        updateNavigationState()
    }

    suspend fun saveBeforeExternalPlayerLaunch() {
        externalPlayerLaunchTimeMs = System.currentTimeMillis()
        savedStateHandle[KEY_PENDING_RESULT] = true
        val status = _state.value.status as? PlayerStatus.Ready ?: return
        val pos = status.positionMs
        var dur = if (status.durationMs > 0) status.durationMs else _state.value.duration

        // HLS streams often lack duration in the external player (VLC returns extra_duration=0).
        // Resolve it from the playlist so completion can still be detected on return.
        // DO NOT BLOCK launch.
        if (dur <= 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                val resolved: Long? = withTimeoutOrNull(3000) {
                    hlsPlaylistDuration.resolveDurationMs(status.url, status.referer) 
                }
                if (resolved != null && resolved > 0L) {
                    savedStateHandle[KEY_EXTERNAL_DURATION] = resolved
                    _state.update { state ->
                        state.copy(duration = resolved, status = (state.status as? PlayerStatus.Ready)?.copy(durationMs = resolved) ?: state.status)
                    }
                    AppLogger.d("ExternalPlayer", "Resolved HLS duration (async): $resolved ms")
                }
            }
        } else {
            savedStateHandle.set<Long>(KEY_EXTERNAL_DURATION, dur)
        }

        // Capture current state
        val currentContentId = this.contentId
        val currentEpisodeId = this.episodeId
        val currentTitle = this.title
        val currentPoster = this.poster
        val currentPageUrl = this.pageUrl
        val currentReferer = this.referer
        val currentSeasons = this.seasons.toList()
        val currentStreams = this.availableStreams.toList()

        val seasonsJson = if (currentSeasons.isNotEmpty()) serializeSeasons(currentSeasons) else null
        val fallbackUrls = currentStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")
        
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            watchProgressRepository.saveProgress(
                currentContentId, currentEpisodeId, pos, dur, currentTitle, currentPoster, currentPageUrl,
                status.url, status.streamType.name, currentReferer, fallbackUrls, seasonsJson = seasonsJson
            )
        }
    }

    fun initPickerColumns() {
        pendingSeason = this.season
        pendingEpisode = this.episode
        pendingVoiceover = this.voiceover
        rebuildPickerColumns()
    }

    fun rebuildPickerColumns() {
        val cols = mutableListOf<PickerColumn>()

        val seasons = _state.value.availableSeasons
        if (seasons != null && seasons.isNotEmpty()) {
            val allEpisodesAreOne = seasons.all { season ->
                season.episodes.all { it.number <= 1 }
            }

            if (pendingSeason == null) pendingSeason = this.season ?: seasons.first().number
            val sNum = pendingSeason!!
            val currentSeasonData = seasons.find { it.number == sNum } ?: seasons.first()
            val eps = currentSeasonData.episodes.sortedBy { it.number }

            if (pendingEpisode == null) pendingEpisode = this.episode ?: eps.firstOrNull()?.number ?: 1
            val eNum = pendingEpisode!!

            val voOptions = currentSeasonData.voiceoverOptions.filter { it.isNotBlank() }
            if (pendingVoiceover == null) {
                pendingVoiceover = this.voiceover.takeIf { it != null && voOptions.contains(it) } ?: voOptions.firstOrNull()
            }

            if (!allEpisodesAreOne) {
                cols.add(PickerColumn(
                    id = "season",
                    label = "СЕЗОН",
                    value = sNum.toString(),
                    needsCommit = true
                ))

                cols.add(PickerColumn(
                    id = "episode",
                    label = "СЕРІЯ",
                    value = eNum.toString(),
                    needsCommit = true
                ))
            }

            if (voOptions.size > 1) {
                cols.add(PickerColumn(
                    id = "voiceover",
                    label = "ОЗВУЧКА",
                    value = pendingVoiceover ?: voOptions.first(),
                    needsCommit = true
                ))
            }
        }

        val prevIndex = _state.value.pickerFocusedIndex
        _state.update { it.copy(pickerColumns = cols) }
        val coercedIndex = prevIndex.coerceIn(0, cols.lastIndex.coerceAtLeast(0))
        if (coercedIndex != prevIndex) _state.update { it.copy(pickerFocusedIndex = coercedIndex) }
    }

    fun onPickerColumnFocused(index: Int) {
        _state.update { it.copy(pickerFocusedIndex = index) }
    }

    fun cycleAudioMode(direction: Int) {
        _state.update { it.copy(audioMode = it.audioMode.cycle(direction)) }
    }

    fun onPickerValueChange(direction: Int) {
        val idx = _state.value.pickerFocusedIndex
        val col = _state.value.pickerColumns.getOrNull(idx) ?: return
        when (col.id) {
            "season" -> changePendingSeason(direction)
            "episode" -> changePendingEpisode(direction)
            "voiceover" -> changePendingVoiceover(direction)
        }
    }

    fun onPickerCommit() {
        val idx = _state.value.pickerFocusedIndex
        val col = _state.value.pickerColumns.getOrNull(idx) ?: return
        if (!col.needsCommit) return

        val s = pendingSeason ?: this.season ?: return
        val e = pendingEpisode ?: this.episode ?: return
        onEpisodeSelected(s, e, pendingVoiceover)
    }

    private fun changePendingSeason(direction: Int) {
        val seasons = _state.value.availableSeasons ?: return
        val current = pendingSeason ?: this.season ?: seasons.first().number
        val newIdx = (seasons.indexOfFirst { it.number == current } + direction).coerceIn(0, seasons.lastIndex)
        val newSeasonData = seasons[newIdx]
        pendingSeason = newSeasonData.number
        pendingEpisode = newSeasonData.episodes.firstOrNull()?.number ?: 1
        pendingVoiceover = null
        rebuildPickerColumns()
    }

    private fun changePendingEpisode(direction: Int) {
        val seasons = _state.value.availableSeasons ?: return
        val sNum = pendingSeason ?: this.season ?: seasons.first().number
        val currentSeasonData = seasons.find { it.number == sNum } ?: seasons.first()
        val eps = currentSeasonData.episodes.sortedBy { it.number }
        val current = pendingEpisode ?: this.episode ?: eps.firstOrNull()?.number ?: 1
        val newIdx = (eps.indexOfFirst { it.number == current } + direction).coerceIn(0, eps.lastIndex)
        pendingEpisode = eps[newIdx].number
        rebuildPickerColumns()
    }

    private fun changePendingVoiceover(direction: Int) {
        val seasons = _state.value.availableSeasons ?: return
        val sNum = pendingSeason ?: this.season ?: seasons.first().number
        val season = seasons.find { it.number == sNum } ?: return
        val options = season.voiceoverOptions.filter { it.isNotBlank() }
        if (options.size < 2) return
        val current = pendingVoiceover ?: this.voiceover ?: options.first()
        val idx = options.indexOf(current)
        if (idx == -1) return
        val newIdx = (idx + direction + options.size) % options.size
        pendingVoiceover = options[newIdx]
        rebuildPickerColumns()
    }

    override fun onCleared() {
        releaseEngine()
        super.onCleared()
    }

    companion object {
        const val KEY_CONTENT_ID = "ext_content_id"
        const val KEY_PAGE_URL = "ext_page_url"
        const val KEY_SEASON = "ext_season"
        const val KEY_EPISODE = "ext_episode"
        const val KEY_TITLE = "ext_title"
        const val KEY_POSTER = "ext_poster"
        const val KEY_PENDING_RESULT = "ext_pending_result"
        private const val KEY_VOICEOVER = "ext_voiceover"
        const val KEY_EXTERNAL_DURATION = "ext_external_duration"
        private const val MIN_EXTERNAL_PLAYBACK_MS_FOR_COMPLETION = 90_000L
    }
}
