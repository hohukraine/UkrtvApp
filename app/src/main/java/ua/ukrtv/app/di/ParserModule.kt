package ua.ukrtv.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.parsing.EneyidaSeasonParser
import ua.ukrtv.app.data.parsing.UakinoSeasonParser
import ua.ukrtv.app.data.parsing.EneyidaParser
import ua.ukrtv.app.data.parsing.UakinoParser
import ua.ukrtv.app.data.parsing.ContentParser
import ua.ukrtv.app.data.streaming.HlsExtractor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    @Provides
    @Singleton
    fun provideHlsExtractor(): HlsExtractor = HlsExtractor()

    @Provides
    @Singleton
    fun provideEneyidaParser(
        htmlHttpClient: HtmlHttpClient,
        hlsExtractor: HlsExtractor,
        eneyidaApi: ua.ukrtv.app.data.api.EneyidaApi,
        unifiedStreamProvider: ua.ukrtv.app.data.streaming.UnifiedStreamProvider
    ): EneyidaParser {
        return EneyidaParser(htmlHttpClient, hlsExtractor, eneyidaApi, unifiedStreamProvider)
    }

    @Provides
    @Singleton
    fun provideUakinoParser(
        htmlHttpClient: HtmlHttpClient,
        hlsExtractor: ua.ukrtv.app.data.streaming.HlsExtractor,
        uakinoApi: ua.ukrtv.app.data.api.UakinoApi,
        unifiedStreamProvider: ua.ukrtv.app.data.streaming.UnifiedStreamProvider
    ): UakinoParser {
        return UakinoParser(htmlHttpClient, hlsExtractor, uakinoApi, unifiedStreamProvider)
    }

    @Provides
    @Singleton
    fun provideEneyidaSeasonParser(
        htmlHttpClient: HtmlHttpClient,
        eneyidaApi: ua.ukrtv.app.data.api.EneyidaApi
    ): EneyidaSeasonParser {
        return EneyidaSeasonParser(htmlHttpClient, eneyidaApi)
    }

    @Provides
    @Singleton
    fun provideUakinoSeasonParser(
        htmlHttpClient: HtmlHttpClient,
        uakinoApi: ua.ukrtv.app.data.api.UakinoApi
    ): UakinoSeasonParser {
        return UakinoSeasonParser(htmlHttpClient, uakinoApi)
    }
}
