package ua.ukrtv.app.player

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
class ExoPlayerEngine(
    private val player: ExoPlayer,
    private val httpDataSourceFactory: OkHttpDataSource.Factory
) : PlaybackEngine {

    private val listeners = mutableListOf<PlaybackEngine.EngineListener>()

    override val currentPosition: Long get() = player.currentPosition
    override val duration: Long get() = player.duration
    override val bufferedPosition: Long get() = player.bufferedPosition
    override val isPlaying: Boolean get() = player.isPlaying
    override val playbackState: Int get() = player.playbackState

    private val exoListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
            if (playbackState == Player.STATE_ENDED) {
                listeners.forEach { it.onEndReached() }
            }
        }

        override fun onVideoSizeChanged(newVideoSize: VideoSize) {
            val codecName = extractCodecName()
            listeners.forEach { it.onCodecInfoChanged(codecName, newVideoSize.width, newVideoSize.height) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            notifyAudioTracks(tracks)
            val codecName = extractCodecName()
            val w = player.videoSize.width
            val h = player.videoSize.height
            if (w > 0 && h > 0) {
                listeners.forEach { it.onCodecInfoChanged(codecName, w, h) }
            }
            listeners.forEach { it.onTracksChanged(tracks) }
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onError(error) }
        }
    }

    init {
        player.addListener(exoListener)
    }

    override fun setSurface(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
        player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
    }

    override fun setMedia(url: String, positionMs: Long, referer: String) {
        applyReferer(referer)
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .build()
        player.setMediaItem(mediaItem)
        if (positionMs > 0) player.seekTo(positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun applyReferer(referer: String) {
        if (referer.isNotBlank()) {
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to referer))
        } else {
            httpDataSourceFactory.setDefaultRequestProperties(emptyMap())
        }
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun release() {
        player.removeListener(exoListener)
        player.release()
    }

    override fun setVideoScalingMode(mode: Int) {
        val exoMode = when (mode) {
            PlaybackEngine.SCALING_ZOOM,
            PlaybackEngine.SCALING_FILL -> androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            else -> androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
        player.setVideoScalingMode(exoMode)
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun getVideoTracks(): Array<PlaybackEngine.TrackDescription> {
        return extractTracksOfType(player.currentTracks, C.TRACK_TYPE_VIDEO)
    }

    override fun setVideoTrack(trackId: Int) {
        selectTrackByTypeAndIndex(C.TRACK_TYPE_VIDEO, trackId)
    }

    override fun getAudioTracks(): Array<PlaybackEngine.TrackDescription> {
        return extractTracksOfType(player.currentTracks, C.TRACK_TYPE_AUDIO)
    }

    override fun setAudioTrack(trackId: Int) {
        selectTrackByTypeAndIndex(C.TRACK_TYPE_AUDIO, trackId)
    }

    override fun getSpuTracks(): Array<PlaybackEngine.TrackDescription> {
        return extractTracksOfType(player.currentTracks, C.TRACK_TYPE_TEXT)
    }

    override fun setSpuTrack(trackId: Int) {
        selectTrackByTypeAndIndex(C.TRACK_TYPE_TEXT, trackId)
    }

    override fun getVideoWidth(): Int = player.videoSize.width
    override fun getVideoHeight(): Int = player.videoSize.height

    override fun addListener(listener: PlaybackEngine.EngineListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlaybackEngine.EngineListener) {
        listeners.remove(listener)
    }

    private fun extractTracksOfType(tracks: Tracks, type: Int): Array<PlaybackEngine.TrackDescription> {
        val result = mutableListOf<PlaybackEngine.TrackDescription>()
        var trackIndex = 0
        for (group in tracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                val fmt = group.getTrackFormat(i)
                val name = buildString {
                    fmt.language?.let { append(it) }
                    if (isNotEmpty()) append(" ")
                    fmt.label?.let { append(it) }
                    if (isBlank()) append("Track ${trackIndex + 1}")
                }
                result.add(PlaybackEngine.TrackDescription(trackIndex, name))
                trackIndex++
            }
        }
        return result.toTypedArray()
    }

    private fun selectTrackByTypeAndIndex(type: Int, targetIndex: Int) {
        var currentIndex = 0
        for (group in player.currentTracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                if (currentIndex == targetIndex) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    val params = player.trackSelectionParameters.buildUpon()
                    params.setOverrideForType(override)
                    player.trackSelectionParameters = params.build()
                    return
                }
                currentIndex++
            }
        }
    }

    private fun extractCodecName(): String {
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            val fmt = group.getTrackFormat(0)
            return fmt.codecs?.substringBefore(".")
                ?: fmt.sampleMimeType?.substringAfterLast("/")
                ?: ""
        }
        return ""
    }

    private fun notifyAudioTracks(tracks: Tracks) {
        val audioTracks = extractTracksOfType(tracks, C.TRACK_TYPE_AUDIO)
        listeners.forEach { it.onAudioTracksChanged(audioTracks) }
    }
}
