package com.tidal.wear.ui.home

import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.DownloadedTrackSummary
import com.tidal.wear.recent.RecentItem
import com.tidal.wear.recent.RecentItemType
import com.tidal.wear.recent.toTrack

/** A local Home item retains the context needed for its direct action. */
sealed interface HomeReadyItem {
    val id: String
    val title: String
    val subtitle: String
    val artworkUrl: String?

    data class Track(
        val track: TidalTrack,
        val downloaded: Boolean,
    ) : HomeReadyItem {
        override val id: String get() = track.id
        override val title: String get() = track.title
        override val subtitle: String get() = track.artist
        override val artworkUrl: String? get() = track.artworkUrl
    }

    data class Album(val recent: RecentItem) : HomeReadyItem {
        override val id: String get() = recent.id
        override val title: String get() = recent.title
        override val subtitle: String get() = recent.subtitle
        override val artworkUrl: String? get() = recent.artworkUrl
    }

    data class Playlist(val recent: RecentItem) : HomeReadyItem {
        override val id: String get() = recent.id
        override val title: String get() = recent.title
        override val subtitle: String get() = recent.subtitle
        override val artworkUrl: String? get() = recent.artworkUrl
    }
}

/** The two Home shelves are selected independently so one cannot starve the other. */
data class HomeShelfContent(
    val offlineItems: List<HomeReadyItem.Track>,
    val recentItems: List<HomeReadyItem>,
)

/**
 * Selects deterministic, local-only Home shelves. Downloads win only for the
 * same track identity; collection recents use a type-qualified key so an
 * album or playlist is never erased by a downloaded track with the same ID.
 * Each shelf has its own limit, keeping recent context visible even when a
 * watch has many downloaded tracks.
 */
fun selectHomeShelfContent(
    downloadedTracks: List<DownloadedTrackSummary>,
    recentItems: List<RecentItem>,
    offlineLimit: Int = 3,
    recentLimit: Int = 3,
): HomeShelfContent {
    if (offlineLimit <= 0 && recentLimit <= 0) return HomeShelfContent(emptyList(), emptyList())
    val downloaded = downloadedTracks.map { HomeReadyItem.Track(it.toTrack(), downloaded = true) }
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        .distinctBy { it.id }
        .take(offlineLimit.coerceAtLeast(0))
    val downloadedIds = downloaded.mapTo(mutableSetOf()) { it.id }
    val recent = recentItems.mapNotNull { item ->
        when (item.type) {
            RecentItemType.Track -> HomeReadyItem.Track(item.toTrack(), downloaded = false)
            RecentItemType.Album -> HomeReadyItem.Album(item)
            RecentItemType.Playlist -> HomeReadyItem.Playlist(item)
        }
    }
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        .filterNot { it is HomeReadyItem.Track && it.id in downloadedIds }
        .distinctBy { item ->
            val type = when (item) {
                is HomeReadyItem.Track -> "track"
                is HomeReadyItem.Album -> "album"
                is HomeReadyItem.Playlist -> "playlist"
            }
            "$type:${item.id}"
        }
        .take(recentLimit.coerceAtLeast(0))
    return HomeShelfContent(
        offlineItems = downloaded,
        recentItems = recent,
    )
}

/** Compatibility projection for callers that need one mixed, bounded list. */
fun selectHomeReadyItems(
    downloadedTracks: List<DownloadedTrackSummary>,
    recentItems: List<RecentItem>,
    limit: Int = 3,
): List<HomeReadyItem> = selectHomeShelfContent(
    downloadedTracks = downloadedTracks,
    recentItems = recentItems,
    offlineLimit = limit,
    recentLimit = limit,
).let { shelf -> (shelf.offlineItems + shelf.recentItems).take(limit) }
