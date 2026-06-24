package ua.ukrtv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.domain.model.HomeData
import javax.inject.Inject

sealed class HomeState {
    data class Loading(val brandColor: Long = 0xFF1E3A8A) : HomeState()
    data class Success(
        val sections: List<HomeSection>,
        val bannerMovie: Movie? = null,
        val brandColor: Long = 0xFF1E3A8A,
        val providers: List<Provider> = emptyList(),
        val currentProviderId: String = ""
    ) : HomeState()
    data class Error(val message: String) : HomeState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: ContentRepository,
    private val providerManager: ProviderManager
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading())
    val state: StateFlow<HomeState> = _state

    init {
        loadHomeContent()
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            combine(
                mediaRepository.getHomeSections(),
                mediaRepository.getContinueWatching(),
                providerManager.brandColor,
                providerManager.activeProvider
            ) { homeSectionsResult, continueWatching, brandColor, activeProvider ->
                val finalSections = mutableListOf<HomeSection>()
                if (continueWatching.isNotEmpty()) {
                    finalSections.add(HomeSection("Продовжити перегляд", continueWatching))
                }
                homeSectionsResult.onSuccess { sections ->
                    finalSections.addAll(sections)
                }
                val homeData = HomeData(null, finalSections)
                Triple(homeData, brandColor, activeProvider)
            }.collect { (homeData, brandColor, activeProvider) ->
                val longBrandColor = (brandColor.toLong() and 0xFFFFFFFFL)
                val banner = homeData.bannerMovie
                    ?: homeData.sections.firstOrNull { it.items.isNotEmpty() }?.items?.randomOrNull()
                _state.value = HomeState.Success(
                    sections = homeData.sections,
                    bannerMovie = banner,
                    brandColor = longBrandColor,
                    providers = providerManager.getProviders(),
                    currentProviderId = activeProvider.name
                )
            }
        }
    }

    fun dismissContinueWatching(movie: Movie) {
        viewModelScope.launch {
            mediaRepository.removeFromContinueWatching(movie)
            // Filter out removed item from current state
            val current = _state.value
            if (current is HomeState.Success) {
                val updatedSections = current.sections.map { section ->
                    if (section.title == "Продовжити перегляд") {
                        section.copy(items = section.items.filter { it.id != movie.id })
                    } else section
                }.filter { it.items.isNotEmpty() }
                _state.value = current.copy(sections = updatedSections)
            }
        }
    }

    fun switchProvider(providerId: String) {
        providerManager.setActiveProvider(providerId)
        loadHomeContent()
    }
}
