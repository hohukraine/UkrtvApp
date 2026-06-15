package ua.ukrtv.app.data.api

import retrofit2.http.*
import ua.ukrtv.app.data.api.model.uakino.UakinoPlaylistResponse

interface UakinoApi {

    @GET("{path}")
    suspend fun getPage(
        @Path("path", encoded = true) path: String,
        @Query("page") page: Int? = null
    ): String

    @FormUrlEncoded
    @POST("engine/ajax/playlists.php")
    suspend fun getPlaylist(
        @Field("news_id") newsId: String,
        @Field("xfield") xfield: String,
        @Field("time") time: String
    ): UakinoPlaylistResponse

    @FormUrlEncoded
    @POST("engine/ajax/search.php")
    suspend fun search(
        @Field("q") query: String
    ): String
}
