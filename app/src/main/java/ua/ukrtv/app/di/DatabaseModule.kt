package ua.ukrtv.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import ua.ukrtv.app.data.local.AppDatabase
import ua.ukrtv.app.data.local.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ukrtv_db")
            .setQueryExecutor(Dispatchers.IO.asExecutor())
            .setTransactionExecutor(Dispatchers.IO.asExecutor())
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
            .build()
    }

    @Provides @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides @Singleton
    fun provideHtmlCacheDao(database: AppDatabase): HtmlCacheDao = database.htmlCacheDao()

    @Provides @Singleton
    fun provideWatchlistDao(database: AppDatabase): WatchlistDao = database.watchlistDao()

    @Provides @Singleton
    fun provideWatchProgressDao(database: AppDatabase): WatchProgressDao = database.watchProgressDao()

    @Provides @Singleton
    fun provideCatalogIndexDao(database: AppDatabase): CatalogIndexDao = database.catalogIndexDao()

    @Provides @Singleton
    fun provideHomeCacheDao(database: AppDatabase): HomeCacheDao = database.homeCacheDao()

    @Provides @Singleton
    fun provideTmdbTrendsCacheDao(database: AppDatabase): TmdbTrendsCacheDao = database.tmdbTrendsCacheDao()

    @Provides @Singleton
    fun provideSeriesStructureDao(database: AppDatabase): SeriesStructureDao = database.seriesStructureDao()
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `series_structure` (`url` TEXT NOT NULL, `seasonsJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `provider` TEXT NOT NULL, PRIMARY KEY(`url`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_structure_provider` ON `series_structure` (`provider`)")
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tmdb_trends_cache` ADD COLUMN `itemIdsJson` TEXT NOT NULL DEFAULT '[]'")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tmdb_trends_cache` (`provider` TEXT NOT NULL, `moviesJson` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`provider`))")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `home_cache` (`providerName` TEXT NOT NULL, `sectionsJson` TEXT NOT NULL, `categoriesJson` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, `categoryLastUpdated` INTEGER NOT NULL, PRIMARY KEY(`providerName`))")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM catalog_index")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_contentId ON watch_progress(contentId)")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN streamUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN streamType TEXT DEFAULT NULL")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN referer TEXT DEFAULT NULL")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN seasonsJson TEXT DEFAULT NULL")
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN fallbackUrls TEXT DEFAULT NULL")
    }
}
