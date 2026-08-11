package ua.ukrtv.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ua.ukrtv.app.data.local.dao.SeriesIndexDao
import ua.ukrtv.app.data.local.entity.SeriesIndexEntity
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Precomputed UAFIX series structure (season -> episode numbers) that lets the provider
 * reconstruct season structures offline instead of fetching N season pages (the source of 429
 * storms).
 *
 * The bundled asset ([SEED_ASSET], built by scripts/sync_catalog.py --series-index) seeds the
 * snapshot so the app works offline on first launch. [refreshFromSitemap] then replaces it from
 * the live sitemap in a single request (whole catalog), so new episodes appear without a rebuild.
 */
@Singleton
class SeriesIndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seriesIndexDao: SeriesIndexDao,
    private val htmlHttpClient: HtmlHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var snapshot: SeriesIndexData = loadSeed()

    private val refreshMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val persisted = try {
                seriesIndexDao.get()
            } catch (e: Exception) {
                AppLogger.w("SeriesIndex", "Failed to read persisted index: ${e.message}")
                null
            }
            if (persisted != null && persisted.updatedAt > snapshot.updatedAt) {
                val decoded = try {
                    json.decodeFromString<SeriesIndexData>(persisted.indexJson)
                } catch (e: Exception) {
                    AppLogger.w("SeriesIndex", "Failed to decode persisted index: ${e.message}")
                    null
                }
                if (decoded != null) {
                    snapshot = decoded
                    AppLogger.d("SeriesIndex", "Loaded persisted index from disk (${decoded.uaflix.size} slugs)")
                }
            }
        }
    }

    private fun loadSeed(): SeriesIndexData = try {
        context.assets.open(SEED_ASSET).use { input ->
            json.decodeFromString<SeriesIndexData>(input.readBytes().decodeToString())
        }
    } catch (e: Exception) {
        AppLogger.w("SeriesIndex", "Failed to load seed index: ${e.message}")
        SeriesIndexData()
    }

    /**
     * Rebuilds the index from the live UAFIX sitemap (1 request) and persists it. Safe to call
     * from the periodic worker and on-demand; concurrent calls are coalesced.
     */
    suspend fun refreshFromSitemap(): Boolean = refreshMutex.withLock {
        val xml = withContext(Dispatchers.IO) {
            htmlHttpClient.getHtml(SITEMAP_URL, isAjax = true)
        } ?: run {
            AppLogger.w("SeriesIndex", "Sitemap fetch failed")
            return false
        }

        val rebuilt = withContext(Dispatchers.IO) {
            SitemapIndexParser.parse(xml, updatedAt = System.currentTimeMillis())
        }
        if (rebuilt.uaflix.isEmpty()) {
            AppLogger.w("SeriesIndex", "Sitemap parsed to an empty index, keeping the current one")
            return false
        }

        val entity = SeriesIndexEntity(
            id = 0,
            indexJson = json.encodeToString(SeriesIndexData.serializer(), rebuilt),
            updatedAt = rebuilt.updatedAt
        )
        try {
            withContext(Dispatchers.IO) { seriesIndexDao.upsert(entity) }
        } catch (e: Exception) {
            AppLogger.w("SeriesIndex", "Failed to persist index: ${e.message}")
        }
        snapshot = rebuilt
        AppLogger.d("SeriesIndex", "Index refreshed from sitemap: ${rebuilt.uaflix.size} slugs")
        true
    }

    /** UAFIX: slug -> season -> episode numbers. */
    fun uaflixEpisodes(slug: String): Map<Int, List<Int>>? {
        val seasons = snapshot.uaflix[slug] ?: return null
        if (seasons.isEmpty()) return null
        return seasons.mapNotNull { (s, eps) -> s.toIntOrNull()?.let { it to eps } }.toMap()
    }

    /** Total episode count across all indexed seasons for a slug (for structure-cache completeness checks). */
    fun indexEpisodeCount(slug: String): Int? {
        val seasons = snapshot.uaflix[slug] ?: return null
        if (seasons.isEmpty()) return null
        return seasons.values.sumOf { it.size }
    }

    /** Full URL override for variant-only episodes that have no canonical season-XX-episode-YY URL. */
    fun uaflixVariantUrl(slug: String, season: Int, episode: Int): String? {
        return snapshot.uaflixVariants[slug]
            ?.get(season.toString())
            ?.get(episode.toString())
    }

    fun isIndexedUaflix(slug: String): Boolean = snapshot.uaflix.containsKey(slug)

    fun size(): Int = snapshot.uaflix.size

    fun updatedAt(): Long = snapshot.updatedAt

    companion object {
        private const val SEED_ASSET = "series_index.json"
        const val SITEMAP_URL = "https://uafix.net/sitemap.xml"

        /**
         * Extracts the slug from a UAFIX serial page URL: https://uafix.net/serials/{slug}/.
         * Returns null for season/episode pages and legacy .html URLs that are not index keys.
         */
        fun uaflixSlugFromUrl(url: String): String? {
            val segment = url.trimEnd('/').substringAfterLast('/')
            if (segment.isEmpty()) return null
            if (segment.startsWith("sezon-") || segment.contains("-episode-") || segment.endsWith(".html")) return null
            return segment
        }
    }
}

@Serializable
data class SeriesIndexData(
    val version: Int = 0,
    val updatedAt: Long = 0,
    val uaflix: Map<String, Map<String, List<Int>>> = emptyMap(),
    val uaflixVariants: Map<String, Map<String, Map<String, String>>> = emptyMap()
)
