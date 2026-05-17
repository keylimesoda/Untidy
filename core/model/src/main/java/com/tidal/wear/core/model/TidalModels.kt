package com.tidal.wear.core.model

data class TidalTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val streamUrl: String? = null,
)

data class TidalAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val artworkUrl: String? = null,
)

data class TidalArtist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
)

data class TidalPlaylist(
    val id: String,
    val title: String,
    val creator: String = "",
    val artworkUrl: String? = null,
)

data class TidalSearchResult(
    val tracks: List<TidalTrack> = emptyList(),
    val albums: List<TidalAlbum> = emptyList(),
    val artists: List<TidalArtist> = emptyList(),
    val playlists: List<TidalPlaylist> = emptyList(),
)

data class TidalSection(
    val title: String,
    val tracks: List<TidalTrack>,
)

data class TidalDiscoverSection(
    val title: String,
    val result: TidalSearchResult,
    val subtitle: String = "",
)

enum class AudioPreset {
    BatterySaver,
    Balanced,
    High,
}

data class EffectiveAudioTier(
    val preset: AudioPreset,
    val mimeType: String,
    val targetBitrateKbps: Int,
    val reason: String,
)
