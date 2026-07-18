package ua.ukrtv.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.ukrtv.app.BuildConfig
import ua.ukrtv.app.data.repository.InstallResult
import ua.ukrtv.app.data.repository.UpdateRepository
import ua.ukrtv.app.domain.model.UpdateInfo
import ua.ukrtv.app.player.ExternalPlayerInfo
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.util.PerformanceProfile
import ua.ukrtv.app.util.PlayerPreferences
import ua.ukrtv.app.util.PlayerType
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class NewVersionAvailable(val info: UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object ReadyToInstall : UpdateState()
    object PermissionRequired : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val performancePreferences: PerformancePreferences,
    private val playerPreferences: PlayerPreferences,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    val performanceProfile: StateFlow<PerformanceProfile> = performancePreferences.profile

    val playerType: StateFlow<PlayerType> = playerPreferences.playerType

    val externalPlayerPackage: StateFlow<String> = playerPreferences.externalPlayerPackage

    private val _installedPlayers = MutableStateFlow<List<ExternalPlayerInfo>>(emptyList())
    val installedPlayers: StateFlow<List<ExternalPlayerInfo>> = _installedPlayers.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadedApk: java.io.File? = null

    private val externalPlayerLauncher by lazy { ExternalPlayerLauncher(appContext) }

    init {
        refreshInstalledPlayers()
    }

    fun refreshInstalledPlayers() {
        _installedPlayers.value = externalPlayerLauncher.detectInstalledPlayers()
    }

    fun setPerformanceProfile(profile: PerformanceProfile) {
        performancePreferences.setProfile(profile)
    }

    fun setPlayerType(type: PlayerType) {
        playerPreferences.setPlayerType(type)
    }

    fun setExternalPlayerPackage(packageName: String) {
        playerPreferences.setExternalPlayerPackage(packageName)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val info = updateRepository.checkUpdate()
            if (info != null) {
                if (info.versionCode > BuildConfig.VERSION_CODE) {
                    _updateState.value = UpdateState.NewVersionAvailable(info)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            } else {
                _updateState.value = UpdateState.Error("Не вдалося перевірити оновлення")
            }
        }
    }

    fun downloadAndInstallUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0f)
            val file = updateRepository.downloadApk(info.apkUrl) { progress ->
                _updateState.value = UpdateState.Downloading(progress)
            }
            if (file != null) {
                downloadedApk = file
                _updateState.value = UpdateState.ReadyToInstall
                installUpdate()
            } else {
                _updateState.value = UpdateState.Error("Не вдалося завантажити оновлення")
            }
        }
    }

    fun installUpdate() {
        val file = downloadedApk ?: return
        when (val result = updateRepository.installApk(file)) {
            is InstallResult.Success -> {
                _updateState.value = UpdateState.Idle
            }
            is InstallResult.PermissionRequired -> {
                _updateState.value = UpdateState.PermissionRequired
            }
            is InstallResult.Error -> {
                _updateState.value = UpdateState.Error(result.message)
            }
        }
    }

    fun openInstallPermissionSettings() {
        updateRepository.openInstallPermissionSettings()
    }
}
