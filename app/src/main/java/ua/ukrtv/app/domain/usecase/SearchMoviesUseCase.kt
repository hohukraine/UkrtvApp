package ua.ukrtv.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.repository.MediaRepository
import javax.inject.Inject

class SearchMoviesUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    operator fun invoke(query: String): Flow<Result<List<Movie>>> = repository.search(query)
}
