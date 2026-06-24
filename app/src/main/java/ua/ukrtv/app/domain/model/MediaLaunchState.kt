package ua.ukrtv.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class MediaLaunchState : Parcelable {
    @Parcelize
    object Idle : MediaLaunchState()
    
    @Parcelize
    data class Resolving(val title: String) : MediaLaunchState()
    
    @Parcelize
    data class Ready(
        val streamResult: StreamResolutionResult,
        val contentId: String,
        val title: String,
        val subtitle: String = "",
        val posterUrl: String = "",
        val season: Int? = null,
        val episode: Int? = null,
        val seasons: List<Season>? = null
    ) : MediaLaunchState()
    
    @Parcelize
    data class Error(val message: String) : MediaLaunchState()
}
