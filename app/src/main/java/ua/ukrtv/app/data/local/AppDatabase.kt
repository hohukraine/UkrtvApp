package ua.ukrtv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.local.dao.SeriesIndexDao
import ua.ukrtv.app.data.local.dao.SeriesStructureDao
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.local.dao.WatchlistDao
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity
import ua.ukrtv.app.data.local.entity.HtmlCacheEntity
import ua.ukrtv.app.data.local.entity.SearchHistoryEntity
import ua.ukrtv.app.data.local.entity.SeriesIndexEntity
import ua.ukrtv.app.data.local.entity.SeriesStructureEntity
import ua.ukrtv.app.data.local.entity.WatchProgressEntity
import ua.ukrtv.app.data.local.entity.WatchlistEntity

import ua.ukrtv.app.data.local.dao.HomeCacheDao
import ua.ukrtv.app.data.local.entity.HomeCacheEntity
import ua.ukrtv.app.data.local.dao.TmdbTrendsCacheDao
import ua.ukrtv.app.data.local.entity.TmdbTrendsCacheEntity

@Database(entities = [
    SearchHistoryEntity::class,
    HtmlCacheEntity::class,
    WatchlistEntity::class,
    WatchProgressEntity::class,
    CatalogIndexEntity::class,
    HomeCacheEntity::class,
    TmdbTrendsCacheEntity::class,
    SeriesStructureEntity::class,
    SeriesIndexEntity::class
], version = 21, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun htmlCacheDao(): HtmlCacheDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun catalogIndexDao(): CatalogIndexDao
    abstract fun homeCacheDao(): HomeCacheDao
    abstract fun tmdbTrendsCacheDao(): TmdbTrendsCacheDao
    abstract fun seriesStructureDao(): SeriesStructureDao
    abstract fun seriesIndexDao(): SeriesIndexDao
}
