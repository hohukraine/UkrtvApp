package ua.ukrtv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.local.dao.WatchlistDao
import ua.ukrtv.app.data.local.entity.HtmlCacheEntity
import ua.ukrtv.app.data.local.entity.SearchHistoryEntity
import ua.ukrtv.app.data.local.entity.WatchlistEntity

@Database(entities = [
    SearchHistoryEntity::class,
    HtmlCacheEntity::class,
    WatchlistEntity::class
], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun htmlCacheDao(): HtmlCacheDao
    abstract fun watchlistDao(): WatchlistDao
}
