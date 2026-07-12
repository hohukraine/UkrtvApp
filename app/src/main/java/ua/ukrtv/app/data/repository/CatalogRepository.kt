package ua.ukrtv.app.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
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

    private val _state = MutableStateFlow(CatalogIndexState())
    val state: StateFlow<CatalogIndexState> = _state.asStateFlow()

    init {
        scope.launch { importFromAssetIfEmpty() }
    }

    private suspend fun importFromAssetIfEmpty() {
        _state.value = _state.value.copy(isBuilding = true, progress = "Importing catalog index...")

        try {
            val uCount = catalogDao.countByProvider("Uakino")
            val eCount = catalogDao.countByProvider("Eneyida")
            if (uCount > 1000 && eCount > 1000) {
                _state.value = _state.value.copy(
                    uakinoReady = true, eneyidaReady = true,
                    uakinoCount = uCount, eneyidaCount = eCount,
                    isBuilding = false, progress = ""
                )
                return
            }
        } catch (_: Exception) { }

        try {
            context.assets.open("catalog_index.json").use { stream ->
                val json = stream.bufferedReader().readText()
                val arr = JSONArray(json)
                val items = mutableListOf<CatalogIndexEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    items.add(CatalogIndexEntity(
                        url = obj.optString("url", ""),
                        title = obj.optString("title", "").lowercase(),
                        titleEn = obj.optString("titleEn", ""),
                        poster = obj.optString("poster", ""),
                        provider = obj.optString("provider", "Uakino"),
                        year = obj.optString("year", ""),
                        rating = obj.optString("rating", ""),
                        quality = obj.optString("quality", ""),
                        contentType = obj.optString("contentType", ""),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    ))
                }
                items.chunked(500).forEach { chunk -> catalogDao.insertAll(chunk) }

                val uakinoCount = items.count { it.provider == "Uakino" }
                val eneyidaCount = items.count { it.provider == "Eneyida" }
                _state.value = _state.value.copy(
                    uakinoReady = uakinoCount > 1000,
                    eneyidaReady = eneyidaCount > 1000,
                    uakinoCount = uakinoCount,
                    eneyidaCount = eneyidaCount,
                    isBuilding = false,
                    progress = ""
                )
                AppLogger.i("CatalogRepository", "Imported ${items.size} items from asset ($uakinoCount Uakino, $eneyidaCount Eneyida)")
            }
        } catch (e: Exception) {
            AppLogger.w("CatalogRepository", "Asset import failed: ${e.message}")
            _state.value = _state.value.copy(isBuilding = false)
            ensureBuilt()
        }
    }

    fun ensureBuilt() {
        val s = _state.value
        if (s.isBuilding) return
        if (s.uakinoReady && s.eneyidaReady) return

        scope.launch {
            _state.value = _state.value.copy(isBuilding = true)

            if (!s.uakinoReady) {
                _state.value = _state.value.copy(progress = "Building Uakino index...")
                val result = if (s.uakinoCount > 0) {
                    val existingUrls = catalogDao.getUrlsByProvider("Uakino").toSet()
                    builder.buildForProviderIncremental(UakinoProfile, CatalogIndexBuilder.UakinoSources, existingUrls)
                } else {
                    builder.buildForProvider(UakinoProfile, CatalogIndexBuilder.UakinoSources)
                }
                val newTotal = s.uakinoCount + result.itemsInserted
                AppLogger.i("CatalogRepository", "Uakino: +${result.itemsInserted} new items, ${result.pagesScanned} pages, ${result.errors} errors")
                _state.value = _state.value.copy(
                    uakinoReady = newTotal > 1000,
                    uakinoCount = newTotal
                )
            }

            if (!s.eneyidaReady) {
                _state.value = _state.value.copy(progress = "Building Eneyida index...")
                val result = if (s.eneyidaCount > 0) {
                    val existingUrls = catalogDao.getUrlsByProvider("Eneyida").toSet()
                    builder.buildForProviderIncremental(EneyidaProfile, CatalogIndexBuilder.EneyidaSources, existingUrls)
                } else {
                    builder.buildForProvider(EneyidaProfile, CatalogIndexBuilder.EneyidaSources)
                }
                val newTotal = s.eneyidaCount + result.itemsInserted
                AppLogger.i("CatalogRepository", "Eneyida: +${result.itemsInserted} new items, ${result.pagesScanned} pages, ${result.errors} errors")
                _state.value = _state.value.copy(
                    eneyidaReady = newTotal > 1000,
                    eneyidaCount = newTotal
                )
            }

            _state.value = _state.value.copy(isBuilding = false, progress = "")
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

    suspend fun updateCatalogSuspend() {
        if (_state.value.isBuilding) return
        _state.value = _state.value.copy(isBuilding = true)

        try {
            val uCount = catalogDao.countByProvider("Uakino")
            if (uCount > 0) {
                val existingUrls = catalogDao.getUrlsByProvider("Uakino").toSet()
                val result = builder.buildForProviderIncremental(UakinoProfile, CatalogIndexBuilder.UakinoSources, existingUrls)
                val newTotal = uCount + result.itemsInserted
                _state.value = _state.value.copy(uakinoReady = newTotal > 1000, uakinoCount = newTotal)
            }
        } catch (e: Exception) {
            AppLogger.w("CatalogRepository", "Uakino incremental update failed: ${e.message}")
        }

        try {
            val eCount = catalogDao.countByProvider("Eneyida")
            if (eCount > 0) {
                val existingUrls = catalogDao.getUrlsByProvider("Eneyida").toSet()
                val result = builder.buildForProviderIncremental(EneyidaProfile, CatalogIndexBuilder.EneyidaSources, existingUrls)
                val newTotal = eCount + result.itemsInserted
                _state.value = _state.value.copy(eneyidaReady = newTotal > 1000, eneyidaCount = newTotal)
            }
        } catch (e: Exception) {
            AppLogger.w("CatalogRepository", "Eneyida incremental update failed: ${e.message}")
        }

        _state.value = _state.value.copy(isBuilding = false, progress = "")
    }

    fun rebuild() {
        scope.launch {
            try { catalogDao.deleteAll() } catch (_: Exception) { }
            _state.value = CatalogIndexState(isBuilding = true, progress = "Rebuilding all indexes...")

            val uResult = builder.buildForProvider(UakinoProfile, CatalogIndexBuilder.UakinoSources)
            _state.value = _state.value.copy(
                uakinoReady = uResult.itemsInserted > 1000,
                uakinoCount = uResult.itemsInserted,
                progress = "Uakino done (${uResult.itemsInserted}), building Eneyida..."
            )

            val eResult = builder.buildForProvider(EneyidaProfile, CatalogIndexBuilder.EneyidaSources)
            _state.value = _state.value.copy(
                eneyidaReady = eResult.itemsInserted > 1000,
                eneyidaCount = eResult.itemsInserted,
                isBuilding = false,
                progress = ""
            )
        }
    }
}
