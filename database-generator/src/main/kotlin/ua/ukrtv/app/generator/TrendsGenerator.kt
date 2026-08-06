package ua.ukrtv.app.generator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.ukrtv.app.matching.MatchCandidate
import ua.ukrtv.app.matching.SearchScorer
import ua.ukrtv.app.matching.TrendsSeedFile
import ua.ukrtv.app.matching.TrendsSeedItem
import ua.ukrtv.app.matching.TrendsSeedProvider
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class CatalogEntry(
    val url: String = "",
    val title: String = "",
    val titleEn: String = "",
    val poster: String = "",
    val provider: String = "",
    val year: String = "",
    val rating: String = "",
    val quality: String = "",
    val contentType: String = "",
    val updatedAt: Long = 0
)

@Serializable
private data class TmdbPage(val results: List<TmdbItem> = emptyList())

@Serializable
private data class TmdbItem(
    val id: Long = 0,
    @SerialName("media_type") val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val originalTitleValue: String get() = originalTitle ?: originalName ?: ""
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
}

private val json = Json { ignoreUnknownKeys = true }

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private fun fetchTmdbItems(apiKey: String): List<TmdbItem> {
    val results = mutableListOf<TmdbItem>()
    val endpoints = listOf(
        "trending/all/week" to null,
        "movie/popular" to "movie",
        "tv/popular" to "tv"
    )
    for ((path, mediaType) in endpoints) {
        for (page in 1..2) {
            val url = "https://api.themoviedb.org/3/$path?api_key=$apiKey&language=uk-UA&page=$page"
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        System.err.println("TMDB HTTP ${response.code} for $path page $page")
                        return@use
                    }
                    val parsed = json.decodeFromString<TmdbPage>(body)
                    val mapped = if (mediaType != null) {
                        parsed.results.map { it.copy(mediaType = mediaType) }
                    } else {
                        parsed.results
                    }
                    results += mapped
                }
            } catch (e: Exception) {
                System.err.println("TMDB fetch failed for $path page $page: ${e.message}")
            }
            Thread.sleep(300)
        }
    }
    return results.distinctBy { it.id }
}

/**
 * Mirrors [CatalogRepository.searchByProviderWordsExact] (single-SQL word-AND search over
 * title/titleEn, up to 20 candidates, г/ґ variants, dedup by url) so the bundled seed
 * matches what the app itself would have found at runtime.
 */
private fun matchItem(catalog: List<CatalogEntry>, item: TmdbItem): CatalogEntry? {
    val queries = listOf(item.originalTitleValue, item.displayTitle)
        .map { SearchScorer.normalizeTitle(it) }
        .filter { it.length >= 2 }
        .distinct()
    if (queries.isEmpty()) return null

    val candidates = mutableListOf<CatalogEntry>()
    val seen = mutableSetOf<String>()
    for (q in queries) {
        val variants = SearchScorer.gVariants(q).take(4)
        for (v in variants) {
            val words = v.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (words.isEmpty()) continue
            for (e in catalog) {
                if (seen.contains(e.url)) continue
                val haystack = (e.title + " " + e.titleEn).lowercase()
                if (words.all { haystack.contains(it) }) {
                    candidates.add(e)
                    seen.add(e.url)
                    if (candidates.size >= 20) break
                }
            }
            if (candidates.size >= 20) break
        }
        if (candidates.size >= 20) break
    }
    if (candidates.isEmpty()) return null

    val matchQueries = listOf(item.originalTitleValue, item.displayTitle)
        .mapNotNull { SearchScorer.cleanSearchQuery(it).takeIf { s -> s.isNotEmpty() } }
        .ifEmpty { queries }

    val best = SearchScorer.pickBestMatch(candidates.map { it.asMatchCandidate() }, matchQueries, item.year)
    return best?.let { b -> candidates.firstOrNull { it.url == b.pageUrl } }
}

private fun matchForProvider(catalog: List<CatalogEntry>, provider: String, items: List<TmdbItem>): List<TrendsSeedItem> {
    val scoped = catalog.filter { it.provider == provider }
    val matched = mutableListOf<TrendsSeedItem>()
    for (item in items) {
        val movie = matchItem(scoped, item) ?: continue
        matched.add(
            TrendsSeedItem(
                tmdbId = item.id,
                url = movie.url,
                title = movie.title,
                poster = movie.poster,
                provider = movie.provider,
                year = movie.year,
                rating = movie.rating,
                quality = movie.quality,
                contentType = movie.contentType
            )
        )
    }
    return matched
}

private fun CatalogEntry.asMatchCandidate() = object : MatchCandidate {
    override val title = this@asMatchCandidate.title
    override val pageUrl = this@asMatchCandidate.url
    override val year = this@asMatchCandidate.year.toIntOrNull()
}

fun main(args: Array<String>) {
    val apiKey = args.getOrNull(0) ?: error("Usage: apiKey catalogPath outputPath")
    val catalogPath = args.getOrNull(1) ?: error("Usage: apiKey catalogPath outputPath")
    val outputPath = args.getOrNull(2) ?: "trends_index.json"

    println("TMDB Trends Seed Generator")
    println("Catalog: $catalogPath")
    println("Output: $outputPath")
    println()

    val startTime = System.currentTimeMillis()

    val catalog = json.decodeFromString<List<CatalogEntry>>(File(catalogPath).readText())
    println("Loaded ${catalog.size} catalog entries")

    val items = fetchTmdbItems(apiKey)
    println("TMDB items: ${items.size}")

    val providers = listOf("Uakino", "UAFLIX")
    val seeded = providers.mapNotNull { p ->
        val matched = matchForProvider(catalog, p, items)
        println("$p: matched ${matched.size}/${items.size}")
        if (matched.isEmpty()) null else TrendsSeedProvider(p, matched)
    }

    val file = TrendsSeedFile(generatedAt = System.currentTimeMillis(), providers = seeded)
    val output = Json { prettyPrint = true }.encodeToString(TrendsSeedFile.serializer(), file)
    File(outputPath).also { f ->
        f.parentFile?.mkdirs()
        f.writeText(output)
    }

    val elapsed = (System.currentTimeMillis() - startTime) / 1000
    println("Done in ${elapsed}s — ${output.length / 1024}KB written to $outputPath")
}
