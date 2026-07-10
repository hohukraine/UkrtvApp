package ua.ukrtv.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String
)
