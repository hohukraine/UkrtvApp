package ua.ukrtv.app.data.api

import retrofit2.http.*
import com.google.gson.annotations.SerializedName

data class DlePlaylistResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("response") val response: String
)

interface DleApi {

    @GET("{path}")
    suspend fun getPage(
        @Path("path", encoded = true) path: String,
        @Query("page") page: Int? = null
    ): String

    @FormUrlEncoded
    @POST
    suspend fun getPlaylist(
        @Url url: String,
        @Field("news_id") newsId: String,
        @Field("xfield") xfield: String,
        @Field("time") time: String
    ): DlePlaylistResponse

    @FormUrlEncoded
    @POST
    suspend fun search(
        @Url url: String,
        @Field("q") query: String
    ): String
}
