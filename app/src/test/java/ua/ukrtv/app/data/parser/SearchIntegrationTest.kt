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
import java.util.concurrent.TimeUnit

class SearchIntegrationTest {

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
            println("Error fetching $url: ${e.message}")
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
            println("Error posting to $url: ${e.message}")
            null
        }
    }

    private fun extractSessionHash(html: String): String {
        return Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: ""
    }

    private fun testProviderSearch(profile: DleProviderProfile, category: ContentCategory) {
        val parser = DleParser(profile)
        val listUrl = profile.baseUrl.trimEnd('/') + "/" + profile.categoryPaths[category]!!
        println("\n=== ${profile.name} ${category}: $listUrl ===")
        val listHtml = getHtml(listUrl) ?: return
        val allItems = parser.parseList(listHtml)
        println("Found ${allItems.size} items on category page")
        assertTrue("No items found on ${profile.name} category page", allItems.isNotEmpty())

        val itemsToTest = allItems.filter { it.title.length > 3 }.take(3)
        assertTrue("Not enough items with valid titles", itemsToTest.isNotEmpty())

        val homepageHtml = getHtml(profile.baseUrl) ?: return
        val hash = extractSessionHash(homepageHtml)
        println("Session hash found: ${hash.take(8)}...")

        for (item in itemsToTest) {
            println("\n  --- Searching: \"${item.title}\" ---")
            val body = FormBody.Builder()
                .add("do", "search").add("subaction", "search").add("story", item.title)
                .apply { if (hash.isNotEmpty()) add("user_hash", hash) }
                .build()
            val searchHtml = postForm(profile.baseUrl.trimEnd('/') + "/index.php?do=search", body)
            assertNotNull("Search returned null for: ${item.title}", searchHtml)

            val results = parser.parseSearch(searchHtml!!)
            println("  Results: ${results.size}")
            assertTrue("Search for '${item.title}' returned no results", results.isNotEmpty())

            val match = results.firstOrNull { it.title.contains(item.title.take(10), ignoreCase = true) || item.title.contains(it.title.take(10), ignoreCase = true) }
                ?: results.firstOrNull {
                    val itemSlug = item.pageUrl.substringAfterLast("/").substringBeforeLast(".html")
                    it.pageUrl.contains(itemSlug, ignoreCase = true)
                }
                ?: run {
                    println("  ⚠️ No close match for '${item.title}' (${item.pageUrl}), skipping")
                    continue
                }
            println("  Best match: ${match.title} -> ${match.pageUrl}")

            val detailHtml = getHtml(match.pageUrl) ?: continue
            val detail = parser.parseDetail(detailHtml, match.pageUrl)
            println("  Detail: ${detail.title} | ${detail.year} | genres: ${detail.genres} | rating: ${detail.rating}")

            if (detail.actors.isNotEmpty()) {
                println("  Actors (${detail.actors.size}): ${detail.actors.take(3)}")
            }
            if (detail.director.isNotEmpty()) {
                println("  Director: ${detail.director}")
            }
            if (detail.country.isNotEmpty()) {
                println("  Country: ${detail.country}")
            }
            if (detail.rating != null) {
                println("  Rating: ${detail.rating}")
            }
        }
    }

    @Test
    fun testUakinoSearchMovies() = testProviderSearch(UakinoProfile, ContentCategory.MOVIES)

    @Test
    fun testUakinoSearchSeries() = testProviderSearch(UakinoProfile, ContentCategory.SERIES)

    @Test
    fun testEneyidaSearchMovies() = testProviderSearch(EneyidaProfile, ContentCategory.MOVIES)

    @Test
    fun testEneyidaSearchSeries() = testProviderSearch(EneyidaProfile, ContentCategory.SERIES)
}
