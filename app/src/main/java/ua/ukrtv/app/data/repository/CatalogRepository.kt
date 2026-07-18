package ua.ukrtv.app.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity
import ua.ukrtv.app.data.providers.EneyidaProfile
import ua.ukrtv.app.data.providers.UakinoProfile
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogIndexState(
    val uakinoReady: Boolean = false,
    val eneyidaReady: Boolean = false,
    val uakinoCount: Int = 0,
    val eneyidaCount: Int = 0,
    val isBuilding: Boolean = false,
    val progress: String = ""
)

@Singleton
class CatalogRepository @Inject constructor(
    private val context: Context,
    private val catalogDao: CatalogIndexDao,
    private val builder: CatalogIndexBuilder
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        val eCount = try { catalogDao.countByProvider("Eneyida") } catch (_: Exception) { 0 }
        if (uCount > 1000 && eCount > 1000) {
            _state.update {
                it.copy(uakinoReady = true, eneyidaReady = true, uakinoCount = uCount, eneyidaCount = eCount)
            }
            return
        }

        _state.update { it.copy(isBuilding = true, progress = "Importing catalog index...") }

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
                            "title" -> title = reader.nextString().lowercase()
                            "titleEn" -> titleEn = reader.nextString()
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
                    }
                }
                reader.endArray()

                if (items.isNotEmpty()) {
                    catalogDao.insertAll(items)
                }

                val uakinoCount = catalogDao.countByProvider("Uakino")
                val eneyidaCount = catalogDao.countByProvider("Eneyida")
                _state.update {
                    it.copy(
                        uakinoReady = uakinoCount > 1000,
                        eneyidaReady = eneyidaCount > 1000,
                        uakinoCount = uakinoCount,
                        eneyidaCount = eneyidaCount
                    )
                }
                AppLogger.i("CatalogRepository", "Imported catalog from asset ($uakinoCount Uakino, $eneyidaCount Eneyida)")
            }
        } catch (e: Exception) {
            AppLogger.w("CatalogRepository", "Asset import failed: ${e.message}")
            ensureBuilt()
        }
    }

    fun ensureBuilt() {
        val s = _state.value
        if (s.isBuilding || (s.uakinoReady && s.eneyidaReady)) return
        launchBuild("ensureBuilt") {
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

            if (!_state.value.eneyidaReady) {
                _state.update { it.copy(progress = "Building Eneyida index...") }
                val eCurrent = _state.value.eneyidaCount
                val eResult = if (eCurrent > 0) {
                    val existingUrls = catalogDao.getUrlsByProvider("Eneyida").toSet()
                    builder.buildForProviderIncremental(EneyidaProfile, CatalogIndexBuilder.EneyidaSources, existingUrls)
                } else {
                    builder.buildForProvider(EneyidaProfile, CatalogIndexBuilder.EneyidaSources)
                }
                val newTotal = eCurrent + eResult.itemsInserted
                AppLogger.i("CatalogRepository", "Eneyida: +${eResult.itemsInserted} new items, ${eResult.pagesScanned} pages, ${eResult.errors} errors")
                _state.update { it.copy(eneyidaReady = newTotal > 1000, eneyidaCount = newTotal) }
            }
        }
    }

    suspend fun search(query: String, limit: Int = 30): List<CatalogIndexEntity> {
        return catalogDao.search(query, limit)
    }

    suspend fun searchByProvider(provider: String, query: String, limit: Int = 30): List<CatalogIndexEntity> {
        if (query.length < 2) return emptyList()
        return catalogDao.searchByProvider(provider, query, limit)
    }

    fun isProviderReady(providerName: String): Boolean {
        val s = _state.value
        return when (providerName) {
            "Uakino" -> s.uakinoReady
            "Eneyida" -> s.eneyidaReady
            else -> false
        }
    }

    suspend fun awaitReady() {
        if (!_state.value.isBuilding && _state.value.uakinoReady && _state.value.eneyidaReady) return
        ensureBuilt()
        state.first { !it.isBuilding }
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

            val eCount = catalogDao.countByProvider("Eneyida")
            if (eCount > 0) {
                val existingUrls = catalogDao.getUrlsByProvider("Eneyida").toSet()
                val result = builder.buildForProviderIncremental(EneyidaProfile, CatalogIndexBuilder.EneyidaSources, existingUrls)
                val newTotal = eCount + result.itemsInserted
                AppLogger.i("CatalogRepository", "Eneyida update: +${result.itemsInserted} new items, ${result.pagesScanned} pages, ${result.errors} errors")
                _state.update { it.copy(eneyidaReady = newTotal > 1000, eneyidaCount = newTotal) }
            }
        }
    }

    fun rebuild() {
        val s = _state.value
        if (s.isBuilding) return
        launchBuild("rebuild") {
            _state.update { it.copy(isBuilding = true, progress = "Rebuilding all indexes...") }
            val uResult = builder.buildForProvider(UakinoProfile, CatalogIndexBuilder.UakinoSources)
            _state.update {
                it.copy(
                    uakinoReady = uResult.itemsInserted > 1000,
                    uakinoCount = uResult.itemsInserted,
                    progress = "Uakino done (${uResult.itemsInserted}), building Eneyida..."
                )
            }
            val eResult = builder.buildForProvider(EneyidaProfile, CatalogIndexBuilder.EneyidaSources)
            _state.update {
                it.copy(
                    eneyidaReady = eResult.itemsInserted > 1000,
                    eneyidaCount = eResult.itemsInserted
                )
            }
        }
    }
}
