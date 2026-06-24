package ua.ukrtv.app.player

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ua.ukrtv.app.domain.model.PlaybackStats
import ua.ukrtv.app.data.repository.WatchProgressRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlaybackStatsTracker @Inject constructor(
    private val watchProgressRepository: WatchProgressRepository,
    private val autoFrameRateHelper: AutoFrameRateHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: Player? = null
    private var deviceId: String = "unknown"
    private var contentId: String = ""
    private var episodeId: String? = null
    private var statsJob: Job? = null
    private var trackingActive = false
    private var videoFrameMetadataListener: VideoFrameMetadataListener? = null
    private var playerListener: Player.Listener? = null

    private val _stats = MutableStateFlow<PlaybackStats?>(null)
    val stats: StateFlow<PlaybackStats?> = _stats

    private val droppedFrames = AtomicInteger(0)
    private val bufferUnderruns = AtomicInteger(0)
    private val totalWatchTimeMs = AtomicLong(0L)
    private val frameCount = AtomicInteger(0)
    @Volatile private var lastFrameTimeNs = 0L
    @Volatile private var fpsSum = 0f
    private val fpsSampleCount = AtomicInteger(0)

    fun startTracking(player: Player, deviceId: String, contentId: String, episodeId: String?) {
        if (trackingActive) {
            stopTracking()
        }
        this.player = player
        this.deviceId = deviceId
        this.contentId = contentId
        this.episodeId = episodeId
        this.droppedFrames.set(0)
        this.bufferUnderruns.set(0)
        this.totalWatchTimeMs.set(0L)
        this.frameCount.set(0)
        this.fpsSum = 0f
        this.fpsSampleCount.set(0)
        this.trackingActive = true

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferUnderruns.incrementAndGet()
                    Log.d("PlaybackStats", "Buffer underrun detected")
                }
                if (playbackState == Player.STATE_READY) {
                    updateCurrentFormatStats()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlaybackStats", "Playback error: ${error.errorCodeName}")
                bufferUnderruns.incrementAndGet()
            }
        }
        this.playerListener = listener
        player.addListener(listener)

        val metadataListener = object : VideoFrameMetadataListener {
            override fun onVideoFrameAboutToBeRendered(
                presentationTimeUs: Long,
                releaseTimeNs: Long,
                format: androidx.media3.common.Format,
                mediaFormat: android.media.MediaFormat?
            ) {
                if (lastFrameTimeNs > 0) {
                    val deltaNs = releaseTimeNs - lastFrameTimeNs
                    if (deltaNs > 0) {
                        val fps = 1_000_000_000f / deltaNs
                        if (fps in 10f..120f) {
                            fpsSum += fps
                            fpsSampleCount.incrementAndGet()
                        }
                    }
                }
                lastFrameTimeNs = releaseTimeNs
                frameCount.incrementAndGet()

                autoFrameRateHelper.onVideoFormatChanged(format)
            }
        }
        this.videoFrameMetadataListener = metadataListener
        (player as? ExoPlayer)?.setVideoFrameMetadataListener(metadataListener)

        startPeriodicSaving()
    }

    fun stopTracking() {
        if (!trackingActive) return
        trackingActive = false
        statsJob?.cancel()
        saveFinalStats()
        
        playerListener?.let {
            player?.removeListener(it)
        }
        videoFrameMetadataListener?.let {
            (player as? ExoPlayer)?.clearVideoFrameMetadataListener(it)
        }
        
        playerListener = null
        videoFrameMetadataListener = null
        player = null
    }

    private fun startPeriodicSaving() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (trackingActive) {
                delay(30_000L)
                saveCurrentStats()
            }
        }
    }

    private fun updateCurrentFormatStats() {
        val currentPlayer = player ?: return
        val track = currentPlayer.currentTracks
        val videoFormat = track.groups
            .firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
            ?.getTrackFormat(0)

        if (videoFormat != null) {
            val currentFpsSamples = fpsSampleCount.get()
            _stats.value = PlaybackStats(
                deviceId = deviceId,
                contentId = contentId,
                episodeId = episodeId,
                avgFps = if (currentFpsSamples > 0) fpsSum / currentFpsSamples else 0f,
                droppedFrames = droppedFrames.get(),
                bufferUnderruns = bufferUnderruns.get(),
                totalWatchTimeMs = totalWatchTimeMs.get(),
                formatCodec = videoFormat.codecs?.substringBefore("."),
                formatHeight = videoFormat.height ?: 0,
                formatBitrate = videoFormat.bitrate ?: 0
            )
        }
    }

    private fun saveCurrentStats() {
        val currentFpsSamples = fpsSampleCount.get()
        val stats = PlaybackStats(
            deviceId = deviceId,
            contentId = contentId,
            episodeId = episodeId,
            avgFps = if (currentFpsSamples > 0) fpsSum / currentFpsSamples else 0f,
            droppedFrames = droppedFrames.get(),
            bufferUnderruns = bufferUnderruns.get(),
            totalWatchTimeMs = totalWatchTimeMs.get(),
            formatCodec = _stats.value?.formatCodec,
            formatHeight = _stats.value?.formatHeight ?: 0,
            formatBitrate = _stats.value?.formatBitrate ?: 0
        )
        _stats.value = stats
        scope.launch {
            watchProgressRepository.savePlaybackStats(stats)
        }
    }

    private fun saveFinalStats() {
        saveCurrentStats()
        val currentDeviceId = deviceId
        scope.launch {
            val allStats = watchProgressRepository.getAllStats()
            val deviceStats = allStats.filter { it.deviceId == currentDeviceId }
            Log.i("PlaybackStats", "Device $currentDeviceId: ${deviceStats.size} sessions recorded")
            deviceStats.filter { !it.isHealthy }.forEach {
                Log.w("PlaybackStats", "UNHEALTHY session: ${it.contentId}/${it.episodeId} " +
                        "FPS=${it.avgFps}, dropped=${it.droppedFrames}, underruns=${it.bufferUnderruns}")
            }
        }
    }

    fun incrementWatchTime(deltaMs: Long) {
        totalWatchTimeMs.addAndGet(deltaMs)
    }

    companion object {
        private const val TAG = "PlaybackStats"
    }
}
