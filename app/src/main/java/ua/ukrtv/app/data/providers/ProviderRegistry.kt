package ua.ukrtv.app.data.providers

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRegistry @Inject constructor(
    val uakinoProvider: UakinoProvider,
    val eneyidaProvider: EneyidaProvider
) {
    val providers: List<ContentProvider> = listOf(uakinoProvider, eneyidaProvider)

    fun getById(id: String): ContentProvider? = 
        providers.find { it.javaClass.simpleName == id }
}
