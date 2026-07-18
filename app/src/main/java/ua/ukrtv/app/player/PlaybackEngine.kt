package ua.ukrtv.app.player

import android.view.SurfaceView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks

interface PlaybackEngine {

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
        const val STATE_PLAYING = 5
        const val STATE_PAUSED = 6

        const val SCALING_FIT = 0
        const val SCALING_ZOOM = 1
        const val SCALING_ASPECT_16_9 = 2
        const val SCALING_ASPECT_4_3 = 3
        const val SCALING_FILL = 4
        const val SCALING_ORIGINAL = 5
    }

    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val isPlaying: Boolean
    val playbackState: Int
    val supportsNativeScaling: Boolean get() = false

    fun setSurface(surfaceView: SurfaceView)
    fun setMedia(url: String, positionMs: Long = 0, referer: String = "")
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
    fun setVideoScalingMode(mode: Int)
    fun setVolume(volume: Float)

    fun getVideoTracks(): Array<TrackDescription>
    fun setVideoTrack(trackId: Int)
    fun getAudioTracks(): Array<TrackDescription>
    fun setAudioTrack(trackId: Int)
    fun getSpuTracks(): Array<TrackDescription>
    fun setSpuTrack(trackId: Int)
    fun getVideoWidth(): Int
    fun getVideoHeight(): Int

    fun addListener(listener: EngineListener)
    fun removeListener(listener: EngineListener)

    data class TrackDescription(val id: Int, val name: String)

    interface EngineListener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlaybackStateChanged(state: Int) {}
        fun onPositionChanged(positionMs: Long) {}
        fun onLengthChanged(lengthMs: Long) {}
        fun onError(message: String) {}
        fun onError(error: PlaybackException) {}
        fun onEndReached() {}
        fun onVideoSizeChanged(width: Int, height: Int) {}
        fun onCodecInfoChanged(codecName: String, width: Int, height: Int) {}
        fun onAudioTracksChanged(tracks: Array<TrackDescription>) {}
        fun onTracksChanged(tracks: Tracks) {}
    }
}
