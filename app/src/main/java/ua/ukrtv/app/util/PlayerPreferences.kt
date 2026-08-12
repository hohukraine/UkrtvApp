package ua.ukrtv.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    private val _externalPlayerPackage = MutableStateFlow(readExternalPlayerPackage())
    val externalPlayerPackage: StateFlow<String> = _externalPlayerPackage.asStateFlow()

    private fun readExternalPlayerPackage(): String {
        return prefs.getString(KEY_EXTERNAL_PLAYER_PACKAGE, "internal")
            ?: "internal"
    }

    fun setExternalPlayerPackage(packageName: String) {
        prefs.edit().putString(KEY_EXTERNAL_PLAYER_PACKAGE, packageName).apply()
        _externalPlayerPackage.value = packageName
    }

    companion object {
        private const val KEY_EXTERNAL_PLAYER_PACKAGE = "external_player_package"
    }
}
