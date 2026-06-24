package ua.ukrtv.app.data.parser

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import ua.ukrtv.app.data.providers.*
import java.util.concurrent.TimeUnit

/**
 * Integration-style tests to verify live provider parsing correctness.
 * Running these requires an internet connection.
 */
class ProviderParserTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language" to "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private fun getHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return try {
            client.newCall(request).execute().use { it.body!!.string() }
        } catch (e: Exception) {
            println("Error fetching $url: ${e.message}")
            null
        }
    }

    @Test
    fun testUakinoSpecificSeries() {
        val url = "https://uakino.best/seriesss/fantastic_series/33511-zzovni-4-sezon.html"
        println("\n--- Testing Uakino Series: $url ---")
        val html = getHtml(url) ?: return
        
        val parser = DleParser(UakinoProfile)
        val detail = parser.parseDetail(html, url)
        
        println("Title: ${detail.title}")
        println("Type: ${detail.contentType}")
        
        val mediaSource = DleResolutionUtils.parsePlaylist(html, url, "Uakino")
        if (mediaSource is MediaSource.Series) {
            println("✅ SEASONS FOUND: ${mediaSource.seasons.size}")
            mediaSource.seasons.forEach { s ->
                println("  Season ${s.number}: ${s.episodes.size} episodes")
                s.episodes.take(1).forEach { e ->
                    println("    Example Ep: ${e.title} -> ${e.url}")
                }
            }
        } else if (mediaSource is MediaSource.Movie) {
            println("⚠️ FOUND MOVIE INSTEAD OF SERIES. URL: ${mediaSource.url}")
        } else {
            println("❌ NO MEDIA SOURCE FOUND")
        }
    }

    @Test
    fun testEneyidaSpecificSeries() {
        val url = "https://eneyida.tv/7026-zzovni.html"
        println("\n--- Testing Eneyida Series: $url ---")
        val html = getHtml(url) ?: return
        
        val parser = DleParser(EneyidaProfile)
        val detail = parser.parseDetail(html, url)
        
        println("Title: ${detail.title}")
        println("Type: ${detail.contentType}")
        
        val mediaSource = DleResolutionUtils.parsePlaylist(html, url, "Eneyida")
        if (mediaSource is MediaSource.Series) {
            println("✅ SEASONS FOUND: ${mediaSource.seasons.size}")
            mediaSource.seasons.forEach { s ->
                println("  Season ${s.number}: ${s.episodes.size} episodes")
                s.episodes.take(1).forEach { e ->
                    println("    Example Ep: ${e.title} -> ${e.url}")
                }
            }
        } else if (mediaSource is MediaSource.Movie) {
            println("⚠️ FOUND MOVIE INSTEAD OF SERIES. URL: ${mediaSource.url}")
        } else {
            println("❌ NO MEDIA SOURCE FOUND")
        }
    }

    @Test
    fun testUakinoSearchPages() {
        println("\n=== Uakino Category Crawl ===")
        val profile = UakinoProfile
        val parser = DleParser(profile)
        
        profile.categoryPaths.forEach { (cat, path) ->
            val url = profile.baseUrl.trimEnd('/') + "/" + path
            println("Crawling $cat: $url")
            val html = getHtml(url) ?: return@forEach
            val movies = parser.parseList(html)
            println("  Found ${movies.size} items")
            movies.take(3).forEach { m ->
                println("    - ${m.title} (${m.year}) [${m.type}]")
            }
        }
    }

    @Test
    fun testEneyidaSearchPages() {
        println("\n=== Eneyida Category Crawl ===")
        val profile = EneyidaProfile
        val parser = DleParser(profile)
        
        profile.categoryPaths.forEach { (cat, path) ->
            val url = profile.baseUrl.trimEnd('/') + "/" + path
            println("Crawling $cat: $url")
            val html = getHtml(url) ?: return@forEach
            val movies = parser.parseList(html)
            println("  Found ${movies.size} items")
            movies.take(3).forEach { m ->
                println("    - ${m.title} (${m.year}) [${m.type}]")
            }
        }
    }
}
