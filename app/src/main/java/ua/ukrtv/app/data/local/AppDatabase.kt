package ua.ukrtv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ua.ukrtv.app.data.local.dao.HtmlCacheDao
import ua.ukrtv.app.data.local.dao.SearchCacheDao
import ua.ukrtv.app.data.local.dao.SearchHistoryDao
import ua.ukrtv.app.data.local.entity.HtmlCacheEntity
import ua.ukrtv.app.data.local.entity.SearchCacheEntity
import ua.ukrtv.app.data.local.entity.SearchHistoryEntity

@Database(entities = [
    SearchCacheEntity::class, 
    SearchHistoryEntity::class,
    HtmlCacheEntity::class
], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun htmlCacheDao(): HtmlCacheDao
}
