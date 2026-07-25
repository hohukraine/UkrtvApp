package ua.ukrtv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.local.dao.WatchlistDao
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity
import ua.ukrtv.app.data.local.entity.HtmlCacheEntity
import ua.ukrtv.app.data.local.entity.SearchHistoryEntity
import ua.ukrtv.app.data.local.entity.WatchProgressEntity
import ua.ukrtv.app.data.local.entity.WatchlistEntity

import ua.ukrtv.app.data.local.dao.HomeCacheDao
import ua.ukrtv.app.data.local.entity.HomeCacheEntity

@Database(entities = [
    SearchHistoryEntity::class,
    HtmlCacheEntity::class,
    WatchlistEntity::class,
    WatchProgressEntity::class,
    CatalogIndexEntity::class,
    HomeCacheEntity::class
], version = 17, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun htmlCacheDao(): HtmlCacheDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun catalogIndexDao(): CatalogIndexDao
    abstract fun homeCacheDao(): HomeCacheDao
}
