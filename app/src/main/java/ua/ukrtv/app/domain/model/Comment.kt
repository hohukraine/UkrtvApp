package ua.ukrtv.app.domain.model

import androidx.compose.runtime.Stable

@Stable
data class Comment(
    val author: String,
    val avatar: String,
    val text: String,
    val date: String = ""
)
