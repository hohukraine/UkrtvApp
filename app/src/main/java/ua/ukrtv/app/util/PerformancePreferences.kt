package ua.ukrtv.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformancePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("performance_prefs", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<PerformanceProfile> = _profile.asStateFlow()

    private fun readProfile(): PerformanceProfile {
        val name = prefs.getString(KEY_PROFILE, PerformanceProfile.AUTO.name)
            ?: PerformanceProfile.AUTO.name
        return try {
            PerformanceProfile.valueOf(name)
        } catch (_: IllegalArgumentException) {
            PerformanceProfile.AUTO
        }
    }

    fun getProfile(): PerformanceProfile = _profile.value

    fun setProfile(profile: PerformanceProfile) {
        prefs.edit().putString(KEY_PROFILE, profile.name).apply()
        _profile.value = profile
    }

    companion object {
        private const val KEY_PROFILE = "performance_profile"
    }
}
