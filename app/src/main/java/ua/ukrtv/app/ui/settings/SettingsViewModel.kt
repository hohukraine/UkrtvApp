package ua.ukrtv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.ukrtv.app.BuildConfig
import ua.ukrtv.app.data.repository.UpdateRepository
import ua.ukrtv.app.domain.model.UpdateInfo
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.util.PerformanceProfile
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class NewVersionAvailable(val info: UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val performancePreferences: PerformancePreferences,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    val performanceProfile: StateFlow<PerformanceProfile> = performancePreferences.profile

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadedApk: java.io.File? = null

    fun setPerformanceProfile(profile: PerformanceProfile) {
        performancePreferences.setProfile(profile)
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
        downloadedApk?.let {
            updateRepository.installApk(it)
        }
    }
}
