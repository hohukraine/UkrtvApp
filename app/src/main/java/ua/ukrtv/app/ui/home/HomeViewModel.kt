package ua.ukrtv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.domain.usecase.GetHomeSectionsUseCase
import ua.ukrtv.app.domain.usecase.GetBannerMovieUseCase
import ua.ukrtv.app.data.providers.ProviderManager

import javax.inject.Inject

sealed class HomeState {
    data class Loading(val brandColor: Int) : HomeState()
    data class Success(
        val brandColor: Int,
        val bannerMovie: Movie?,
        val sections: List<HomeSection>,
        val providers: List<Provider> = emptyList(),
        val currentProviderId: String = ""
    ) : HomeState()

    data class Error(val message: String) : HomeState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeSectionsUseCase: GetHomeSectionsUseCase,
    private val getBannerMovieUseCase: GetBannerMovieUseCase,
    private val providerManager: ProviderManager
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<HomeState> = providerManager.activeProvider
        .flatMapLatest { provider ->
            combine(
                getHomeSectionsUseCase(),
                getBannerMovieUseCase()
            ) { sectionsResult, bannerMovie ->
                sectionsResult.fold(
                    onSuccess = { sections ->
                        HomeState.Success(
                            brandColor = providerManager.brandColor.value,
                            bannerMovie = bannerMovie,
                            sections = sections,
                            providers = providerManager.getProviders(),
                            currentProviderId = provider.javaClass.simpleName
                        )
                    },
                    onFailure = { e ->
                        HomeState.Error(e.message ?: "Помилка завантаження")
                    }
                )
            }.onStart {
                emit(HomeState.Loading(providerManager.brandColor.value))
            }
        }
        .debounce(300)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeState.Loading(providerManager.brandColor.value)
        )

    fun switchProvider(providerId: String) {
        viewModelScope.launch {
            providerManager.setActiveProvider(providerId)
        }
    }
}
