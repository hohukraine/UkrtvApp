package ua.ukrtv.app.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity
import ua.ukrtv.app.data.providers.UaflixProfile
import ua.ukrtv.app.data.providers.UakinoProfile
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.matching.SearchScorer
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogIndexState(
    val uakinoReady: Boolean = false,
    val uaflixReady: Boolean = false,
    val uakinoCount: Int = 0,
    val uaflixCount: Int = 0,
    val isBuilding: Boolean = false,
    val progress: String = ""
)

@Singleton
class CatalogRepository @Inject constructor(
    private val context: Context,
    private val catalogDao: CatalogIndexDao,
    private val builder: CatalogIndexBuilder
) {
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())

    private var buildJob: Job? = null

    private val _state = MutableStateFlow(CatalogIndexState())
    val state: StateFlow<CatalogIndexState> = _state.asStateFlow()

    init {
        launchBuild("importFromAssetIfEmpty") {
            importFromAssetIfEmpty()
        }
    }

    private fun launchBuild(tag: String, block: suspend () -> Unit) {
        if (buildJob?.isActive == true) {
            AppLogger.d("CatalogRepository", "Build already in progress, skipping ($tag)")
            return
        }
        buildJob = scope.launch {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.e("CatalogRepository", "Build failed ($tag): ${e.message}", e)
            } finally {
                _state.update { it.copy(isBuilding = false, progress = "") }
            }
        }
    }

    private suspend fun importFromAssetIfEmpty() {
        val uCount = try { catalogDao.countByProvider("Uakino") } catch (_: Exception) { 0 }
        val uaCount = try { catalogDao.countByProvider("UAFLIX") } catch (_: Exception) { 0 }
        if (uCount > 1000 && uaCount > 1000) {
            _state.update {
                it.copy(uakinoReady = true, uaflixReady = true, uakinoCount = uCount, uaflixCount = uaCount)
            }
            return
        }

        // Mark building BEFORE the delay so awaitReady()/ensureBuilt() block on the real
        // import instead of returning early against an empty catalog (first-launch race).
        _state.update { it.copy(isBuilding = true, progress = "Importing catalog index...") }

        // Phase 3: Delay catalog import to avoid competition with Home content loading
        delay(8000)

        try { catalogDao.deleteByProviderNotIn(listOf("Uakino", "UAFLIX")) } catch (_: Exception) { }

        try {
            context.assets.open("catalog_index.json").use { stream ->
                val reader = android.util.JsonReader(stream.bufferedReader())
                val items = mutableListOf<CatalogIndexEntity>()

                reader.beginArray()
                while (reader.hasNext()) {
                    var url = ""
                    var title = ""
                    var titleEn = ""
                    var poster = ""
                    var provider = "Uakino"
                    var year = ""
                    var rating = ""
                    var quality = ""
                    var contentType = ""
                    var updatedAt = System.currentTimeMillis()

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "url" -> url = reader.nextString()
                            "title" -> title = reader.nextString().lowercase(java.util.Locale.ROOT)
                            "titleEn" -> titleEn = reader.nextString().lowercase(java.util.Locale.ROOT)
                            "poster" -> poster = reader.nextString()
                            "provider" -> provider = reader.nextString()
                            "year" -> year = reader.nextString()
                            "rating" -> rating = reader.nextString()
                            "quality" -> quality = reader.nextString()
                            "contentType" -> contentType = reader.nextString()
                            "updatedAt" -> updatedAt = reader.nextLong()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (url.isNotEmpty()) {
                        items.add(CatalogIndexEntity(
                            url = url, title = title, titleEn = titleEn, poster = poster,
                            provider = provider, year = year, rating = rating, quality = quality,
                            contentType = contentType, updatedAt = updatedAt
                        ))
                    }

                    if (items.size >= 500) {
                        catalogDao.insertAll(items.toList())
                        items.clear()
                        kotlinx.coroutines.yield()
                    }
                }
                reader.endArray()

                if (items.isNotEmpty()) {
                    catalogDao.insertAll(items)
                }

                val uakinoCount = catalogDao.countByProvider("Uakino")
                val uaflixCount = catalogDao.countByProvider("UAFLIX")
                _state.update {
                    it.copy(
                        uakinoReady = uakinoCount > 1000,
                        uaflixReady = uaflixCount > 1000,
                        uakinoCount = uakinoCount,
                        uaflixCount = uaflixCount
                    )
                }
                AppLogger.i("CatalogRepository", "Imported catalog from asset ($uakinoCount Uakino, $uaflixCount UAFLIX)")
            }
        } catch (e: Exception) {
            AppLogger.w("CatalogRepository", "Asset import failed: ${e.message}")
            ensureBuilt()
        }
    }

    fun ensureBuilt() {
        val s = _state.value
        if (s.isBuilding || (s.uakinoReady && s.uaflixReady)) return
        launchBuild("ensureBuilt") {
            try { catalogDao.deleteByProviderNotIn(listOf("Uakino", "UAFLIX")) } catch (_: Exception) { }
            _state.update { it.copy(isBuilding = true, progress = "Building catalog index...") }

            if (!_state.value.uakinoReady) {
                _state.update { it.copy(progress = "Building Uakino index...") }
                val uCurrent = _state.value.uakinoCount
                val uResult = if (uCurrent > 0) {
                    val existingUrls = catalogDao.getUrlsByProvider("Uakino").toSet()
                    builder.buildForProviderIncremental(UakinoProfile, CatalogIndexBuilder.UakinoSources, existingUrls)
                } else {
                    builder.buildForProvider(UakinoProfile, CatalogIndexBuilder.UakinoSources)
                }
                val newTotal = uCurrent + uResult.itemsInserted
                AppLogger.i("CatalogRepository", "Uakino: +${uResult.itemsInserted} new items, ${uResult.pagesScanned} pages, ${uResult.errors} errors")
                _state.update { it.copy(uakinoReady = newTotal > 1000, uakinoCount = newTotal) }
            }

            if (!_state.value.uaflixReady) {
                _state.update { it.copy(progress = "Building UAFLIX index...") }
                val uaCurrent = _state.value.uaflixCount
                val uaResult = if (uaCurrent > 0) {
                    val existingUrls = catalogDao.getUrlsByProvider("UAFLIX").toSet()
                    builder.buildForProviderIncremental(UaflixProfile, CatalogIndexBuilder.UaflixSources, existingUrls)
                } else {
                    builder.buildForProvider(UaflixProfile, CatalogIndexBuilder.UaflixSources)
                }
                val newTotal = uaCurrent + uaResult.itemsInserted
                AppLogger.i("CatalogRepository", "UAFLIX: +${uaResult.itemsInserted} new items, ${uaResult.pagesScanned} pages, ${uaResult.errors} errors")
                _state.update { it.copy(uaflixReady = newTotal > 1000, uaflixCount = newTotal) }
            }
        }
    }

    suspend fun search(query: String, limit: Int = 30): List<CatalogIndexEntity> {
        return catalogDao.search(query.trim().lowercase(), limit)
    }

    suspend fun searchByProvider(provider: String, query: String, limit: Int = 30): List<CatalogIndexEntity> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val variants = SearchScorer.gVariants(q).take(4)
        if (variants.size == 1) return catalogDao.searchByProvider(provider, variants[0], limit)
        val results = mutableListOf<CatalogIndexEntity>()
        val seen = mutableSetOf<String>()
        for (v in variants) {
            for (e in catalogDao.searchByProvider(provider, v, limit)) {
                if (seen.add(e.url)) results.add(e)
            }
        }
        return results.take(limit)
    }

    /**
     * Word-based per-provider search with a sliding recall chain: for each word position
     * in the query it tries a 3-word, 2-word and single-word window, so leading words that
     * the catalog dropped (numbers like "13" in "13 годин", ordinal prefixes, etc.) don't
     * block retrieval. г/ґ spelling variants of the query are all tried, since the catalog
     * keeps each site's raw spelling.
     */
    suspend fun searchByProviderWords(provider: String, query: String, limit: Int = 30): List<CatalogIndexEntity> {
        val canonical = query.trim().lowercase().replace('ґ', 'г')
        val variants = SearchScorer.gVariants(canonical).take(4)
        if (variants.all { it.isBlank() }) return emptyList()

        val results = mutableListOf<CatalogIndexEntity>()
        val seen = mutableSetOf<String>()
        fun add(entities: List<CatalogIndexEntity>) {
            for (e in entities) if (seen.add(e.url)) results.add(e)
        }

        for (v in variants) {
            val words = v.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (words.isEmpty()) continue
            val before = results.size
            for (i in words.indices) {
                val w1 = words[i]
                val w2 = words.getOrNull(i + 1)
                val w3 = words.getOrNull(i + 2)
                if (w2 != null) {
                    if (w3 != null) {
                        add(catalogDao.searchByProviderThreeWords(provider, w1, w2, w3, limit))
                    }
                    add(catalogDao.searchByProviderTwoWords(provider, w1, w2, limit))
                }
            }
            if (results.size == before) {
                for (w in words) add(catalogDao.searchByProvider(provider, w, limit))
            }
        }
        return results.take(limit)
    }

    /**
     * Single-SQL word search: every word of the query must appear (in any order) in
     * title or titleEn. Built dynamically so punctuation/order inside the title never
     * blocks the match, while staying one query per variant instead of the multi-query
     * sliding-window chain used by [searchByProviderWords].
     */
    suspend fun searchByProviderWordsExact(provider: String, query: String, limit: Int = 20): List<CatalogIndexEntity> {
        val canonical = query.trim().lowercase().replace('ґ', 'г')
        val variants = SearchScorer.gVariants(canonical).take(4)
        if (variants.all { it.isBlank() }) return emptyList()

        val results = mutableListOf<CatalogIndexEntity>()
        val seen = mutableSetOf<String>()
        for (v in variants) {
            val words = v.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (words.isEmpty()) continue
            for (e in catalogDao.searchByProviderWordsRaw(buildWordAndQuery(provider, words, limit))) {
                if (seen.add(e.url)) results.add(e)
            }
            if (results.size >= limit) break
        }
        return results.take(limit)
    }

    private fun buildWordAndQuery(provider: String, words: List<String>, limit: Int): SupportSQLiteQuery {
        val sb = StringBuilder("SELECT * FROM catalog_index WHERE provider = ?")
        val args = mutableListOf<Any>(provider)
        for (w in words) {
            sb.append(" AND (title LIKE '%' || ? || '%' OR titleEn LIKE '%' || ? || '%')")
            args.add(w)
            args.add(w)
        }
        sb.append(" LIMIT ?")
        args.add(limit)
        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }

    fun isProviderReady(providerName: String): Boolean {
        val s = _state.value
        return when (providerName) {
            "Uakino" -> s.uakinoReady
            "UAFLIX" -> s.uaflixReady
            else -> false
        }
    }

    /**
     * Waits only until the given provider's index is ready, so search/trends don't block
     * on the other provider being built or imported. The other provider is still ensured
     * when needed, but never holds the active-provider path back.
     */
    suspend fun awaitProviderReady(providerName: String) {
        if (isProviderReady(providerName)) return
        ensureBuilt()
        state.first { !it.isBuilding || isProviderReady(providerName) }
    }

    fun updateCatalogSuspend() {
        val s = _state.value
        if (s.isBuilding) return
        launchBuild("updateCatalogSuspend") {
            _state.update { it.copy(isBuilding = true, progress = "Updating catalog index...") }

            val uCount = catalogDao.countByProvider("Uakino")
            if (uCount > 0) {
                val existingUrls = catalogDao.getUrlsByProvider("Uakino").toSet()
                val result = builder.buildForProviderIncremental(UakinoProfile, CatalogIndexBuilder.UakinoSources, existingUrls)
                val newTotal = uCount + result.itemsInserted
                AppLogger.i("CatalogRepository", "Uakino update: +${result.itemsInserted} new items, ${result.pagesScanned} pages, ${result.errors} errors")
                _state.update { it.copy(uakinoReady = newTotal > 1000, uakinoCount = newTotal) }
            }

            val uaCount = catalogDao.countByProvider("UAFLIX")
            if (uaCount > 0) {
                val existingUrls = catalogDao.getUrlsByProvider("UAFLIX").toSet()
                val result = builder.buildForProviderIncremental(UaflixProfile, CatalogIndexBuilder.UaflixSources, existingUrls)
                val newTotal = uaCount + result.itemsInserted
                AppLogger.i("CatalogRepository", "UAFLIX update: +${result.itemsInserted} new items, ${result.pagesScanned} pages, ${result.errors} errors")
                _state.update { it.copy(uaflixReady = newTotal > 1000, uaflixCount = newTotal) }
            }
        }
    }

    fun rebuild() {
        val s = _state.value
        if (s.isBuilding) return
        launchBuild("rebuild") {
            try { catalogDao.deleteByProviderNotIn(listOf("Uakino", "UAFLIX")) } catch (_: Exception) { }
            _state.update { it.copy(isBuilding = true, progress = "Rebuilding all indexes...") }
            val uResult = builder.buildForProvider(UakinoProfile, CatalogIndexBuilder.UakinoSources)
            _state.update {
                it.copy(
                    uakinoReady = uResult.itemsInserted > 1000,
                    uakinoCount = uResult.itemsInserted,
                    progress = "Uakino done (${uResult.itemsInserted}), building UAFLIX..."
                )
            }
            val uaResult = builder.buildForProvider(UaflixProfile, CatalogIndexBuilder.UaflixSources)
            _state.update {
                it.copy(
                    uaflixReady = uaResult.itemsInserted > 1000,
                    uaflixCount = uaResult.itemsInserted
                )
            }
        }
    }
}
