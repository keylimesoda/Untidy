package com.tidal.wear.ui.search

import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack

/** A search result with the common fields needed by the mixed results UI. */
sealed interface SearchResultItem {
    val id: String
    val label: String
    val order: Int

    data class Track(val value: TidalTrack, override val order: Int) : SearchResultItem {
        override val id: String get() = "track:${value.id}"
        override val label: String get() = value.title
    }

    data class Album(val value: TidalAlbum, override val order: Int) : SearchResultItem {
        override val id: String get() = "album:${value.id}"
        override val label: String get() = value.title
    }

    data class Artist(val value: TidalArtist, override val order: Int) : SearchResultItem {
        override val id: String get() = "artist:${value.id}"
        override val label: String get() = value.name
    }

    data class Playlist(val value: TidalPlaylist, override val order: Int) : SearchResultItem {
        override val id: String get() = "playlist:${value.id}"
        override val label: String get() = value.title
    }
}

/**
 * Returns a small mixed set for the top of a search result. Scores are deliberately
 * simple and deterministic: exact, prefix, and contains matches win in that order;
 * source order is retained for ties.
 */
fun topSearchResults(
    result: TidalSearchResult,
    query: String,
    limit: Int = 6,
): List<SearchResultItem> {
    if (limit <= 0) return emptyList()
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) return emptyList()

    val all = buildList {
        result.tracks.forEachIndexed { index, value -> add(SearchResultItem.Track(value, index)) }
        val albumOffset = result.tracks.size
        result.albums.forEachIndexed { index, value -> add(SearchResultItem.Album(value, albumOffset + index)) }
        val artistOffset = albumOffset + result.albums.size
        result.artists.forEachIndexed { index, value -> add(SearchResultItem.Artist(value, artistOffset + index)) }
        val playlistOffset = artistOffset + result.artists.size
        result.playlists.forEachIndexed { index, value -> add(SearchResultItem.Playlist(value, playlistOffset + index)) }
    }

    return all
        .distinctBy(SearchResultItem::id)
        .map { item -> item to searchMatchScore(normalizeSearchText(item.label), normalizedQuery) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<SearchResultItem, Int>> { it.second }.thenBy { it.first.order })
        .take(limit)
        .map { it.first }
}

internal fun normalizeSearchText(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")

private fun searchMatchScore(label: String, query: String): Int = when {
    label == query -> 3
    label.startsWith(query) -> 2
    label.contains(query) -> 1
    else -> 0
}
