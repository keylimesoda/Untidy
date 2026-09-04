package com.tidal.wear.ui.home

import com.tidal.wear.core.playback.offline.DownloadedTrackSummary
import com.tidal.wear.recent.RecentItem
import com.tidal.wear.recent.RecentItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReadyItemsTest {
    @Test
    fun downloadedTracksComeFirstAndDuplicatesAreRemoved() {
        val downloaded = listOf(summary("same", "Downloaded copy"), summary("local", "Local"))
        val recent = listOf(recent(RecentItemType.Track, "same", "Recent copy"), recent(RecentItemType.Track, "recent", "Recent"))

        val result = selectHomeReadyItems(downloaded, recent)

        assertEquals(listOf("same", "local", "recent"), result.map(HomeReadyItem::id))
        assertEquals("Downloaded copy", result.first().title)
        assertEquals(listOf(true, true, false), result.map { (it as HomeReadyItem.Track).downloaded })
    }

    @Test
    fun recentCollectionsKeepTheirTypeAndCanShareAnIdWithTrack() {
        val result = selectHomeReadyItems(
            downloadedTracks = listOf(summary("shared", "Local track")),
            recentItems = listOf(
                recent(RecentItemType.Album, "shared", "Recently played album"),
                recent(RecentItemType.Playlist, "mix", "Recently played mix"),
            ),
        )

        assertEquals(listOf("shared", "shared", "mix"), result.map(HomeReadyItem::id))
        assertEquals(HomeReadyItem.Track::class, result[0]::class)
        assertEquals(HomeReadyItem.Album::class, result[1]::class)
        assertEquals(HomeReadyItem.Playlist::class, result[2]::class)
    }

    @Test
    fun blankItemsAndItemsPastLimitAreIgnored() {
        val result = selectHomeReadyItems(
            downloadedTracks = listOf(summary("", "No id"), summary("local", "Local")),
            recentItems = listOf(recent(RecentItemType.Album, "recent", "Recent"), recent(RecentItemType.Playlist, "later", "Later")),
            limit = 2,
        )

        assertEquals(listOf("local", "recent"), result.map(HomeReadyItem::id))
    }

    @Test
    fun manyDownloadsDoNotHideRecentCollectionContext() {
        val shelf = selectHomeShelfContent(
            downloadedTracks = listOf(
                summary("local-1", "Local 1"),
                summary("local-2", "Local 2"),
                summary("local-3", "Local 3"),
                summary("local-4", "Local 4"),
            ),
            recentItems = listOf(
                recent(RecentItemType.Album, "album-1", "Recent album"),
                recent(RecentItemType.Playlist, "playlist-1", "Recent playlist"),
            ),
        )

        assertEquals(listOf("local-1", "local-2", "local-3"), shelf.offlineItems.map(HomeReadyItem::id))
        assertEquals(listOf("album-1", "playlist-1"), shelf.recentItems.map(HomeReadyItem::id))
        assertEquals(HomeReadyItem.Album::class, shelf.recentItems[0]::class)
        assertEquals(HomeReadyItem.Playlist::class, shelf.recentItems[1]::class)
    }

    private fun summary(id: String, title: String) = DownloadedTrackSummary(id, title, "Artist", 0L)

    private fun recent(type: RecentItemType, id: String, title: String) = RecentItem(
        type = type,
        id = id,
        title = title,
        subtitle = "Artist",
        artworkUrl = null,
        lastPlayedAt = 0L,
    )

}
