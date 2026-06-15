package ua.ukrtv.app.data.providers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.data.model.ParsedMovie
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.AppLogger

@Parcelize
data class SearchItem(
    val title: String,
    val url: String,
    val imageUrl: String,
    val provider: String,
    val type: ContentType = ContentType.MOVIE,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val shortDescription: String? = null
) : Parcelable

sealed class MediaSource : Parcelable {
    abstract val primaryUrl: String?
    abstract val referer: String
    abstract val providerName: String
    abstract val allUrls: List<String>

    @Parcelize
    data class Movie(
        val url: String,
        val fallbackUrls: List<String> = emptyList(),
        override val referer: String = "",
        override val providerName: String = ""
    ) : MediaSource(), Parcelable {
        override val primaryUrl: String get() = url
        override val allUrls: List<String> get() = listOf(url) + fallbackUrls
    }

    @Parcelize
    data class Series(
        val seasons: List<ProviderSeason>,
        override val referer: String = "",
        override val providerName: String = ""
    ) : MediaSource(), Parcelable {
        override val primaryUrl: String? get() = seasons.firstOrNull()?.episodes?.firstOrNull()?.url
        override val allUrls: List<String> get() = seasons.flatMap { s -> s.episodes.map { it.url } }
    }
}

@Parcelize
data class ProviderSeason(
    val number: Int,
    val episodes: List<ProviderEpisode>
) : Parcelable

@Parcelize
data class ProviderEpisode(
    val number: Int,
    val title: String,
    val url: String
) : Parcelable

@Parcelize
data class StreamResult(
    val url: String,
    val source: MediaSource?,
    val providerName: String,
    val hostSource: String,
    val referer: String = "",
    val providerBaseUrl: String = ""
) : Parcelable

interface StreamProvider {
    val name: String
    val baseUrl: String
    val hasPublicSearch: Boolean

    suspend fun getEffectiveBaseUrl(): String = baseUrl

    suspend fun initializeSession(): Boolean
    suspend fun search(query: String, limit: Int = 10): List<SearchItem>
    suspend fun getMediaSource(pageUrl: String): MediaSource?
    suspend fun getMovieDetails(id: String, url: String): MovieDetail

    suspend fun getStreamUrl(pageUrl: String): StreamResult? {
        val source = getMediaSource(pageUrl)
        return when (source) {
            is MediaSource.Movie -> StreamResult(
                url = source.url,
                source = source,
                providerName = name,
                hostSource = "adapter",
                referer = source.referer,
                providerBaseUrl = baseUrl
            )
            is MediaSource.Series -> {
                val firstEp = source.seasons.firstOrNull()?.episodes?.firstOrNull()
                StreamResult(
                    url = firstEp?.url ?: "",
                    source = source,
                    providerName = name,
                    hostSource = "series",
                    referer = source.referer,
                    providerBaseUrl = baseUrl
                )
            }
            null -> null
        }
    }

    fun supportsUrl(url: String): Boolean
    fun clearCache(url: String? = null)
}

abstract class BaseStreamProvider(
    protected val client: OkHttpClient,
    protected val htmlHttpClient: ua.ukrtv.app.data.network.HtmlHttpClient
) : StreamProvider {

    override suspend fun getEffectiveBaseUrl(): String = baseUrl

    override fun clearCache(url: String?) {
        // Cache removed in radical refactoring
    }

    protected suspend fun getHtml(
        url: String,
        referer: String? = null,
        isAjax: Boolean = false
    ): String? = htmlHttpClient.getHtml(url, referer, isAjax)

    protected suspend fun postHtml(
        url: String,
        body: RequestBody,
        referer: String? = null,
        isAjax: Boolean = false
    ): String? = htmlHttpClient.postHtml(url, body, referer, isAjax)

    protected fun absoluteUrl(href: String): String =
        if (href.startsWith("http")) href else baseUrl.trimEnd('/') + (if (href.startsWith("/")) "" else "/") + href

    override suspend fun getMovieDetails(id: String, url: String): MovieDetail {
        throw UnsupportedOperationException("getMovieDetails must be implemented by child or avoided")
    }
}

fun MediaSource.toStreamResolutionResult(
    providerName: String,
    sourcePageUrl: String
): StreamResolutionResult {
    val primary = when (this) {
        is MediaSource.Movie -> this.primaryUrl ?: ""
        is MediaSource.Series -> this.primaryUrl ?: ""
    }

    val streamType = when {
        primary.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
        primary.contains(".mpd", ignoreCase = true) -> StreamType.MPD
        primary.contains(".mp4", ignoreCase = true) -> StreamType.MP4
        else -> StreamType.UNKNOWN
    }

    val fallback = when (this) {
        is MediaSource.Movie -> this.fallbackUrls
        is MediaSource.Series -> emptyList()
    }

    return StreamResolutionResult(
        streamUrl = primary,
        streamType = streamType,
        referer = referer,
        fallbackStreams = fallback,
        providerName = providerName,
        sourcePageUrl = sourcePageUrl,
        source = this
    )
}

fun ProviderSeason.toDomainSeason(): Season = Season(
    number = this.number,
    episodes = this.episodes.map { ep ->
        Episode(
            id = ep.url,
            number = ep.number,
            title = ep.title,
            pageUrl = ep.url
        )
    }
)

fun SearchItem.toMovie(): Movie {
    return Movie(
        id = url.hashCode().toString(),
        title = title,
        poster = imageUrl,
        year = year,
        type = type,
        pageUrl = url,
        genres = genres,
        shortDescription = shortDescription
    )
}

fun ParsedMovie.toMovie(): Movie = Movie(
    id = id,
    title = title,
    poster = poster,
    year = year,
    type = type,
    pageUrl = pageUrl,
    genres = genres,
    shortDescription = shortDescription
)

fun ParsedMovie.toSearchItem(): SearchItem = SearchItem(
    title = title,
    url = pageUrl,
    imageUrl = poster,
    provider = "", // To be set by provider
    type = type,
    year = year,
    genres = genres,
    shortDescription = shortDescription
)
