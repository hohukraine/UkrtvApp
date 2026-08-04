package ua.ukrtv.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogIndexBuilder
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.UpdateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideCatalogIndexBuilder(
        htmlHttpClient: HtmlHttpClient,
        catalogIndexDao: CatalogIndexDao
    ): CatalogIndexBuilder = CatalogIndexBuilder(htmlHttpClient, catalogIndexDao)

    @Provides @Singleton
    fun provideCatalogRepository(
        @ApplicationContext context: Context,
        catalogIndexDao: CatalogIndexDao,
        builder: CatalogIndexBuilder
    ): CatalogRepository = CatalogRepository(context, catalogIndexDao, builder)

    @Provides @Singleton
    fun provideUpdateRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        json: Json
    ): UpdateRepository = UpdateRepository(context, okHttpClient, json)
    @Provides @Singleton
    fun provideStreamResolvingInteractor(
        streamResolver: ua.ukrtv.app.data.streaming.StreamResolver,
        providerManager: ua.ukrtv.app.data.providers.ProviderManager
    ): ua.ukrtv.app.ui.player.StreamResolvingInteractor = ua.ukrtv.app.ui.player.StreamResolvingInteractor(
        streamResolver, providerManager
    )

    @Provides @Singleton
    fun provideExternalPlayerInteractor(
        @ApplicationContext context: Context,
        playerPreferences: ua.ukrtv.app.util.PlayerPreferences
    ): ua.ukrtv.app.ui.player.ExternalPlayerInteractor = ua.ukrtv.app.ui.player.ExternalPlayerInteractor(
        context, playerPreferences
    )
}
