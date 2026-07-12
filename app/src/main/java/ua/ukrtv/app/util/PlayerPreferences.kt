package ua.ukrtv.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlayerType(val label: String) {
    BUILTIN("Вбудований"),
    VLC("VLC")
}

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    private val _playerType = MutableStateFlow(readPlayerType())
    val playerType: StateFlow<PlayerType> = _playerType.asStateFlow()

    private fun readPlayerType(): PlayerType {
        val name = prefs.getString(KEY_PLAYER_TYPE, PlayerType.BUILTIN.name)
            ?: PlayerType.BUILTIN.name
        return try {
            PlayerType.valueOf(name)
        } catch (_: IllegalArgumentException) {
            PlayerType.BUILTIN
        }
    }

    fun getPlayerType(): PlayerType = _playerType.value

    fun setPlayerType(type: PlayerType) {
        prefs.edit().putString(KEY_PLAYER_TYPE, type.name).apply()
        _playerType.value = type
    }

    companion object {
        private const val KEY_PLAYER_TYPE = "player_type"
    }
}
