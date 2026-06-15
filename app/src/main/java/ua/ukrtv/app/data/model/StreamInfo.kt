package ua.ukrtv.app.data.model

data class StreamInfo(
    val hls: String,
    val audioTracks: List<AudioTrack>,
    val subtitles: List<Subtitle>
)