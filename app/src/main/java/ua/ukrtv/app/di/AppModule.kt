package ua.ukrtv.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ua.ukrtv.app.tv.TvRecommendationManager
import ua.ukrtv.app.util.HomePreferences
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.util.PlayerPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }

    @Provides @Singleton
    fun provideTvRecommendationManager(@ApplicationContext context: Context): TvRecommendationManager =
        TvRecommendationManager(context)

    @Provides @Singleton
    fun providePerformancePreferences(@ApplicationContext context: Context): PerformancePreferences =
        PerformancePreferences(context)

    @Provides @Singleton
    fun providePlayerPreferences(@ApplicationContext context: Context): PlayerPreferences =
        PlayerPreferences(context)

    @Provides @Singleton
    fun provideHomePreferences(@ApplicationContext context: Context): HomePreferences =
        HomePreferences(context)
}
