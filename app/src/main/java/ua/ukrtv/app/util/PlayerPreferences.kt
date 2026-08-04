package ua.ukrtv.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlayerType(val label: String) {
    EXTERNAL_PLAYER("Зовнішній плеєр")
}

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    private val _playerType = MutableStateFlow(readPlayerType())
    val playerType: StateFlow<PlayerType> = _playerType.asStateFlow()

    private val _externalPlayerPackage = MutableStateFlow(readExternalPlayerPackage())
    val externalPlayerPackage: StateFlow<String> = _externalPlayerPackage.asStateFlow()

    private fun readPlayerType(): PlayerType {
        return PlayerType.EXTERNAL_PLAYER
    }

    private fun readExternalPlayerPackage(): String {
        return prefs.getString(KEY_EXTERNAL_PLAYER_PACKAGE, "org.videolan.vlc")
            ?: "org.videolan.vlc"
    }

    fun setExternalPlayerPackage(packageName: String) {
        prefs.edit().putString(KEY_EXTERNAL_PLAYER_PACKAGE, packageName).apply()
        _externalPlayerPackage.value = packageName
    }

    companion object {
        private const val KEY_PLAYER_TYPE = "player_type"
        private const val KEY_EXTERNAL_PLAYER_PACKAGE = "external_player_package"
    }
}
