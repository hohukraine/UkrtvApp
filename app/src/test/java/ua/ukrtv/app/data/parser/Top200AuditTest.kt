package ua.ukrtv.app.data.parser

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.providers.DleParser
import ua.ukrtv.app.data.providers.DleProviderProfile
import ua.ukrtv.app.data.providers.EneyidaProfile
import ua.ukrtv.app.data.providers.UakinoProfile
import ua.ukrtv.app.data.repository.Top200Repository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.SearchScorer
import java.util.concurrent.TimeUnit

class Top200AuditTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language" to "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private fun getHtml(url: String): String? {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return try {
            client.newCall(request).execute().use { it.body!!.string() }
        } catch (e: Exception) {
            println("  Error fetching $url: ${e.message}")
            null
        }
    }

    private fun postForm(url: String, body: FormBody): String? {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { it.body!!.string() }
        } catch (e: Exception) {
            println("  Error posting to $url: ${e.message}")
            null
        }
    }

    private fun extractSessionHash(html: String): String {
        return Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: ""
    }

    data class AuditResult(
        val rank: Int,
        val title: String,
        val originalTitle: String,
        val year: String,
        var uakinoFound: Boolean = false,
        var uakinoTimeMs: Long = 0,
        var uakinoMatchTitle: String = "",
        var uakinoConfidence: Float = 0f,
        var eneyidaFound: Boolean = false,
        var eneyidaTimeMs: Long = 0,
        var eneyidaMatchTitle: String = "",
        var eneyidaConfidence: Float = 0f,
    )

    @Test
    fun auditAll200OnBothProviders() {
        val top200 = Top200Repository().getTop200()
        println("=== Top200 AUDIT: ${top200.size} movies ===\n")

        val results = mutableListOf<AuditResult>()

        // Initialize sessions
        println("Initializing sessions...")
        val uakinoHtml = getHtml(UakinoProfile.baseUrl)
        val uakinoHash = if (uakinoHtml != null) extractSessionHash(uakinoHtml) else ""
        val eneyidaHtml = getHtml(EneyidaProfile.baseUrl)
        val eneyidaHash = if (eneyidaHtml != null) extractSessionHash(eneyidaHtml) else ""
        println("Uakino hash: ${uakinoHash.take(8)}...")
        println("Eneyida hash: ${eneyidaHash.take(8)}...")
        println()

        var totalTimeUakino = 0L
        var totalTimeEneyida = 0L
        var foundUakino = 0
        var foundEneyida = 0

        for ((index, movie) in top200.withIndex()) {
            print("[${index + 1}/200] rank=${movie.rank} \"${movie.title}\" (${movie.year})... ")

            val queries = buildList {
                if (movie.originalTitle.isNotBlank()) add(movie.originalTitle)
                if (movie.title.isNotBlank()) add(movie.title)
                movie.year.toIntOrNull()?.let { year ->
                    if (movie.originalTitle.isNotBlank()) add("${movie.originalTitle} $year")
                    if (movie.title.isNotBlank()) add("${movie.title} $year")
                }
                addAll(movie.searchQueries)
                if (movie.originalTitle.isNotBlank()) {
                    val t = SearchScorer.transliterate(movie.originalTitle)
                    if (t.length >= 3) add(t)
                }
            }.distinct()
            val scoringQueries = listOfNotNull(movie.originalTitle, movie.title) + movie.searchQueries
            val expectedYear = movie.year.toIntOrNull()

            val result = AuditResult(movie.rank, movie.title, movie.originalTitle, movie.year)

            // Search Uakino
            if (uakinoHash.isNotEmpty()) {
                val t0 = System.currentTimeMillis()
                val uakinoResult = searchProvider(queries, UakinoProfile, uakinoHash, scoringQueries, expectedYear)
                val elapsed = System.currentTimeMillis() - t0
                result.uakinoTimeMs = elapsed
                if (uakinoResult != null) {
                    result.uakinoFound = true
                    result.uakinoMatchTitle = uakinoResult.title
                    result.uakinoConfidence = uakinoResult.confidence
                    foundUakino++
                }
                totalTimeUakino += elapsed
            }

            // Search Eneyida
            if (eneyidaHash.isNotEmpty()) {
                val t0 = System.currentTimeMillis()
                val eneyidaResult = searchProvider(queries, EneyidaProfile, eneyidaHash, scoringQueries, expectedYear)
                val elapsed = System.currentTimeMillis() - t0
                result.eneyidaTimeMs = elapsed
                if (eneyidaResult != null) {
                    result.eneyidaFound = true
                    result.eneyidaMatchTitle = eneyidaResult.title
                    result.eneyidaConfidence = eneyidaResult.confidence
                    foundEneyida++
                }
                totalTimeEneyida += elapsed
            }

            results.add(result)

            val status = buildString {
                append(if (result.uakinoFound) "U✓" else "U✗")
                append("(${result.uakinoTimeMs}ms) ")
                append(if (result.eneyidaFound) "E✓" else "E✗")
                append("(${result.eneyidaTimeMs}ms)")
            }
            println(status)
        }

        // Summary
        println("\n${"=".repeat(60)}")
        println("SUMMARY")
        println("${"=".repeat(60)}")
        println("Uakino:  $foundUakino/${top200.size} found  (${String.format("%.1f", 100.0 * foundUakino / top200.size)}%)  avg ${totalTimeUakino / top200.size}ms")
        println("Eneyida: $foundEneyida/${top200.size} found  (${String.format("%.1f", 100.0 * foundEneyida / top200.size)}%)  avg ${totalTimeEneyida / top200.size}ms")

        // Not found on EITHER provider
        val notFoundAnywhere = results.filter { !it.uakinoFound && !it.eneyidaFound }
        if (notFoundAnywhere.isNotEmpty()) {
            println("\n⚠️ NOT FOUND ON EITHER PROVIDER (${notFoundAnywhere.size}):")
            notFoundAnywhere.forEach {
                println("  #${it.rank} \"${it.title}\" (${it.year}) [${it.originalTitle}]")
            }
        }

        // Found only on one provider
        val onlyUakino = results.filter { it.uakinoFound && !it.eneyidaFound }
        val onlyEneyida = results.filter { !it.uakinoFound && it.eneyidaFound }
        if (onlyUakino.isNotEmpty()) {
            println("\nUakino-only (${onlyUakino.size}): ${onlyUakino.joinToString { "#${it.rank}" }}")
        }
        if (onlyEneyida.isNotEmpty()) {
            println("\nEneyida-only (${onlyEneyida.size}): ${onlyEneyida.joinToString { "#${it.rank}" }}")
        }

        // Top 10 slowest
        println("\nTop 10 slowest Uakino:")
        results.sortedByDescending { it.uakinoTimeMs }.take(10).forEach {
            println("  #${it.rank} \"${it.title}\" — ${it.uakinoTimeMs}ms")
        }
        println("\nTop 10 slowest Eneyida:")
        results.sortedByDescending { it.eneyidaTimeMs }.take(10).forEach {
            println("  #${it.rank} \"${it.title}\" — ${it.eneyidaTimeMs}ms")
        }

        assertTrue("Uakino found < 50%", foundUakino >= top200.size / 2)
        assertTrue("Eneyida found < 50%", foundEneyida >= top200.size / 2)
    }

    data class SearchMatch(
        val title: String,
        val url: String,
        val confidence: Float
    )

    private fun searchProvider(
        searchQueries: List<String>,
        profile: DleProviderProfile,
        sessionHash: String,
        scoringQueries: List<String>,
        expectedYear: Int?
    ): SearchMatch? {
        val parser = DleParser(profile)
        val allResults = mutableListOf<Movie>()

        for (q in searchQueries) {
            if (q.length < 3 && !(q.length >= 2 && q.any { it.isDigit() })) continue
            if (allResults.size >= 30) break

            val body = FormBody.Builder()
                .add("do", "search").add("subaction", "search").add("story", q)
                .apply { if (sessionHash.isNotEmpty()) add("user_hash", sessionHash) }
                .build()
            val searchHtml = postForm(profile.baseUrl.trimEnd('/') + "/index.php?do=search", body)
            if (searchHtml != null) {
                val parsed = parser.parseSearch(searchHtml)
                if (parsed.isNotEmpty()) {
                    allResults.addAll(parsed)
                    continue
                }
            }

            // Fallback: try AJAX search if no results
            if (allResults.isEmpty()) {
                val ajaxBody = FormBody.Builder()
                    .add("query", q)
                    .apply { if (sessionHash.isNotEmpty()) add("user_hash", sessionHash) }
                    .build()
                val ajaxHtml = postForm(profile.baseUrl.trimEnd('/') + "/engine/ajax/search.php", ajaxBody)
                if (ajaxHtml != null) {
                    val parsed = parser.parseSearch(ajaxHtml)
                    if (parsed.isNotEmpty()) {
                        allResults.addAll(parsed)
                    }
                }
            }
        }

        if (allResults.isEmpty()) return null

        val unique = allResults.distinctBy { it.pageUrl }
        val best = SearchScorer.pickBestMatch(
            results = unique,
            queries = scoringQueries,
            expectedYear = expectedYear
        )

        if (best != null) {
            // Compute confidence
            val variants = SearchScorer.normalizeTitle(best.title)
            val q = scoringQueries.joinToString(" ")
            val confidence = SearchScorer.tokenSetSimilarity(q, variants)
            return SearchMatch(best.title, best.pageUrl, confidence)
        }

        return null
    }
}
