package com.tidal.wear.recent

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentHistoryTest {
    @Test
    fun recordDedupesByTypeAndIdAndMovesLatestToTop() {
        val first = item(RecentItemType.Track, "1", title = "Old", lastPlayedAt = 1)
        val albumSameId = item(RecentItemType.Album, "1", title = "Album", lastPlayedAt = 2)
        val updated = item(RecentItemType.Track, "1", title = "New", lastPlayedAt = 3)

        val result = RecentHistory.record(listOf(first, albumSameId), updated)

        assertEquals(listOf(updated, albumSameId), result)
    }

    @Test
    fun recordCapsAtTwentyItems() {
        val existing = (1..25).map { index ->
            item(RecentItemType.Track, "track-$index", lastPlayedAt = index.toLong())
        }

        val result = RecentHistory.record(existing, item(RecentItemType.Playlist, "playlist", lastPlayedAt = 99))

        assertEquals(20, result.size)
        assertEquals("playlist", result.first().id)
        assertEquals("track-19", result.last().id)
    }

    @Test
    fun recordKeepsArtistsOutBecauseArtistTypeDoesNotExist() {
        val types = RecentItemType.entries.map { it.name }

        assertEquals(listOf("Track", "Album", "Playlist"), types)
    }

    private fun item(
        type: RecentItemType,
        id: String,
        title: String = id,
        lastPlayedAt: Long = 0,
    ) = RecentItem(
        type = type,
        id = id,
        title = title,
        subtitle = type.name,
        artworkUrl = null,
        lastPlayedAt = lastPlayedAt,
    )
}
