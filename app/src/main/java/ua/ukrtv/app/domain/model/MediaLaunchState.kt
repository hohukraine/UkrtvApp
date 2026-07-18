package ua.ukrtv.app.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Stable
import kotlinx.parcelize.Parcelize

@Stable
sealed class MediaLaunchState : Parcelable {
    @Stable
    @Parcelize
    object Idle : MediaLaunchState()
    
    @Stable
    @Parcelize
    data class Resolving(val title: String) : MediaLaunchState()
    
    @Stable
    @Parcelize
    data class Ready(
        val streamResult: StreamResolutionResult,
        val contentId: String,
        val title: String,
        val subtitle: String = "",
        val posterUrl: String = "",
        val season: Int? = null,
        val episode: Int? = null,
        val voiceover: String? = null,
        val seasons: List<Season>? = null
    ) : MediaLaunchState()
    
    @Parcelize
    data class Error(val error: AppError) : MediaLaunchState()
}
