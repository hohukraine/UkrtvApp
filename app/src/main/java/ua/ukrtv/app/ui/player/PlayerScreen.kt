package ua.ukrtv.app.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.BrandBlue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.LocalFormFactor

@Composable
fun PlayerScreen(
    url: String,
    contentId: String,
    title: String,
    poster: String = "",
    season: Int? = null,
    episode: Int? = null,
    brandColor: Color = BrandBlue,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val formFactor = LocalFormFactor.current
    if (formFactor == FormFactor.TV) {
        ExternalPlayerScreen(
            url = url,
            contentId = contentId,
            title = title,
            poster = poster,
            season = season,
            episode = episode,
            onBack = onBack,
            viewModel = viewModel
        )
    } else {
        ExternalPlayerScreen(
            url = url,
            contentId = contentId,
            title = title,
            poster = poster,
            season = season,
            episode = episode,
            onBack = onBack,
            viewModel = viewModel
        )
    }
}
