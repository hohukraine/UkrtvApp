package ua.ukrtv.app.domain.usecase

import kotlinx.coroutines.flow.*
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.repository.MediaRepository
import javax.inject.Inject

class GetHomeSectionsUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    operator fun invoke(): Flow<Result<List<HomeSection>>> = combine(
        repository.getHomeSections(),
        repository.getContinueWatching(),
        repository.getPopularByCategory(ContentCategory.MOVIES),
        repository.getPopularByCategory(ContentCategory.SERIES),
        repository.getPopularByCategory(ContentCategory.ANIME),
        repository.getPopularByCategory(ContentCategory.CARTOONS)
    ) { flowResults ->
        val homeResult = flowResults[0] as Result<List<HomeSection>>
        val continueWatching = flowResults[1] as List<Movie>
        val popularMovies = flowResults[2] as List<Movie>
        val popularSeries = flowResults[3] as List<Movie>
        val anime = flowResults[4] as List<Movie>
        val cartoons = flowResults[5] as List<Movie>

        val finalSections = mutableListOf<HomeSection>()

        // 1. Продовжити перегляд (Завжди зверху, якщо є)
        if (continueWatching.isNotEmpty()) {
            finalSections.add(HomeSection("Продовжити перегляд", continueWatching))
        }

        // 2. Новинки від провайдера
        homeResult.onSuccess { sections ->
            finalSections.addAll(sections)
        }

        // 3. Категорії
        if (popularMovies.isNotEmpty()) finalSections.add(HomeSection("Популярне: Фільми", popularMovies))
        if (popularSeries.isNotEmpty()) finalSections.add(HomeSection("Популярне: Серіали", popularSeries))
        if (anime.isNotEmpty()) finalSections.add(HomeSection("Популярне: Аніме", anime))
        if (cartoons.isNotEmpty()) finalSections.add(HomeSection("Мультфільми", cartoons))

        Result.success(finalSections)
    }.distinctUntilChanged { old, new ->
        // Уникаємо зайвих рекомпозицій, якщо дані по суті не змінилися (порівнюємо ID елементів)
        val oldIds = old.getOrNull()?.flatMap { s -> s.items.map { it.id } }
        val newIds = new.getOrNull()?.flatMap { s -> s.items.map { it.id } }
        oldIds == newIds
    }
}
