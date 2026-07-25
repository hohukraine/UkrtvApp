package ua.ukrtv.app.ui.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.ukrtv.app.player.AudioEngine
import ua.ukrtv.app.player.PlaybackEngine
import ua.ukrtv.app.player.PlayerFactory
import ua.ukrtv.app.player.ExoPlayerEngine
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PlayerType

@UnstableApi
class PlaybackManager(
    private val context: Context,
    private val playerFactory: PlayerFactory,
    private val audioEngine: AudioEngine,
    private val scope: CoroutineScope,
    private val onPlayerError: (androidx.media3.common.PlaybackException) -> Unit,
    private val onPlaybackStateChanged: (Int) -> Unit,
    private val onIsPlayingChanged: (Boolean) -> Unit,
    private val onPositionChanged: (Long) -> Unit
) {
    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private var _engine: PlaybackEngine? = null
    val engine: PlaybackEngine? get() = _engine

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var positionUpdateJob: Job? = null

    fun getOrCreatePlayer(dsFactory: androidx.media3.datasource.DataSource.Factory): ExoPlayer? {
        if (_player == null) {
            try {
                _player = playerFactory.buildPlayer(context, dsFactory)
                _player?.let { 
                    audioEngine.attach(it)
                    it.addListener(playerListener)
                }
            } catch (e: Exception) {
                AppLogger.e("PlaybackManager", "Failed to create player", e)
                return null
            }
        }
        return _player
    }

    fun getOrCreateEngine(playerType: PlayerType, dsFactory: androidx.media3.datasource.DataSource.Factory): PlaybackEngine? {
        if (_engine != null) return _engine
        _engine = when (playerType) {
            PlayerType.BUILTIN -> {
                val p = getOrCreatePlayer(dsFactory) ?: return null
                ExoPlayerEngine(p, dsFactory, null) // Injected from VM if needed
            }
            PlayerType.EXTERNAL_PLAYER -> null
        }
        _engine?.let { startPositionUpdates() }
        return _engine
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            this@PlaybackManager.onPlaybackStateChanged.invoke(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlaybackManager.onIsPlayingChanged.invoke(isPlaying)
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            this@PlaybackManager.onPlayerError.invoke(error)
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _engine?.let { onPositionChanged(it.currentPosition) }
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        _engine?.setVolume(if (newMuted) 0f else 1f)
            ?: run { _player?.volume = if (newMuted) 0f else 1f }
    }

    fun release() {
        stopPositionUpdates()
        _engine?.let { e ->
            if (e is ExoPlayerEngine) {
                val p = e.detachPlayer()
                p?.removeListener(playerListener)
                p?.release()
            } else {
                e.release()
            }
        }
        _engine = null
        _player = null
    }
}
