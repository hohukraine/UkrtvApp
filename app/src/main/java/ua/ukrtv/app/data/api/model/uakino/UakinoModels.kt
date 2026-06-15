package ua.ukrtv.app.data.api.model.uakino

import com.google.gson.annotations.SerializedName

data class UakinoPlaylistResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("response") val response: String
)

data class UakinoSearchResponse(
    val html: String
)
