package ua.ukrtv.app.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class Provider(
    val name: String,
    val brandColor: String
)
