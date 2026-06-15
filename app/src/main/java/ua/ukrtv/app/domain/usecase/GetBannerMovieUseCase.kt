package ua.ukrtv.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.repository.MediaRepository
import javax.inject.Inject

class GetBannerMovieUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    operator fun invoke(): Flow<Movie?> = repository.getBannerMovie()
}
