package ua.ukrtv.app.player

import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioMode(val label: String) {
    NORMAL("Normal"),
    NIGHT("Night"),
    SITCOM("Sitcom"),
}

@Singleton
class AudioEngine @Inject constructor() {

    private var mode: AudioMode = AudioMode.NORMAL
    private var player: Player? = null

    fun getMode(): AudioMode = mode

    fun cycleMode(): AudioMode {
        mode = when (mode) {
            AudioMode.NORMAL -> AudioMode.NIGHT
            AudioMode.NIGHT -> AudioMode.SITCOM
            AudioMode.SITCOM -> AudioMode.NORMAL
        }
        apply()
        return mode
    }

    fun cycleModeReverse(): AudioMode {
        mode = when (mode) {
            AudioMode.NORMAL -> AudioMode.SITCOM
            AudioMode.NIGHT -> AudioMode.NORMAL
            AudioMode.SITCOM -> AudioMode.NIGHT
        }
        apply()
        return mode
    }

    fun setMode(newMode: AudioMode) {
        mode = newMode
        apply()
    }

    fun attach(player: Player) {
        this.player = player
        apply()
    }

    fun release() {
        player = null
        mode = AudioMode.NORMAL
    }

    private fun apply() {
        val p = player ?: return
        p.setVolume(when (mode) {
            AudioMode.NORMAL -> 1.0f
            AudioMode.NIGHT -> 0.5f
            AudioMode.SITCOM -> 0.8f
        })
    }
}
