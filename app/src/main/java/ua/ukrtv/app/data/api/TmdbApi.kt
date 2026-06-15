package ua.ukrtv.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "uk-UA",
        @Query("year") year: String? = null
    ): TmdbSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "uk-UA"
    ): TmdbMovieDetail

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tv_id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "uk-UA"
    ): TmdbTvDetail

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tv_id: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "uk-UA"
    ): TmdbSeasonDetail

    @GET("trending/all/day")
    suspend fun getTrendingAll(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "uk-UA",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "uk-UA",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "uk-UA",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
}

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>
)

data class TmdbSearchResult(
    val id: Int,
    @SerializedName("media_type") val mediaType: String?, // "movie" or "tv"
    val title: String?,
    val name: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("overview") val overview: String?
)

data class TmdbMovieDetail(
    val id: Int,
    val title: String,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    val runtime: Int?
)

data class TmdbTvDetail(
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int?,
    @SerializedName("first_air_date") val firstAirDate: String?
)

data class TmdbSeasonDetail(
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisode>
)

data class TmdbEpisode(
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("still_path") val stillPath: String?,
    @SerializedName("air_date") val airDate: String?
)
