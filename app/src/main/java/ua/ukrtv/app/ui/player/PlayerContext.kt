package ua.ukrtv.app.ui.player

import ua.ukrtv.app.domain.model.Season

data class PlayerContext(
    var contentId: String = "",
    var title: String = "",
    var pageUrl: String = "",
    var poster: String = "",
    var referer: String = "",
    var subtitle: String = "",
    var season: Int? = null,
    var episode: Int? = null,
    var episodeId: String? = null,
    var voiceover: String? = null,
    var retryCount: Int = 0,
    var availableStreams: MutableList<String> = mutableListOf(),
    var currentStreamIndex: Int = 0,
    var seasons: List<Season>? = null
)
