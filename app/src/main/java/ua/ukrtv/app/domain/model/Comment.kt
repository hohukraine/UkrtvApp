package ua.ukrtv.app.domain.model

data class Comment(
    val author: String,
    val avatar: String,
    val text: String,
    val date: String = ""
)
