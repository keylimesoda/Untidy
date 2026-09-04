package com.tidal.wear.ui.search

import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultRankingTest {
    @Test
    fun exactThenPrefixThenContainsAndSourceOrder() {
        val result = TidalSearchResult(
            tracks = listOf(
                track("t1", "The Blue Song"),
                track("t2", "Blue"),
                track("t3", "Blue Horizon"),
                track("t4", "Deep Blue"),
            ),
        )

        assertEquals(
            listOf("Blue", "Blue Horizon", "The Blue Song", "Deep Blue"),
            topSearchResults(result, " blue ").map { it.label },
        )
    }

    @Test
    fun mixedResultsAreCappedAndTypedDuplicatesAreRemoved() {
        val result = TidalSearchResult(
            tracks = listOf(track("t1", "Blue"), track("t2", "Blue Live")),
            albums = listOf(TidalAlbum("a", "Blue", "Artist")),
            artists = listOf(TidalArtist("ar", "Blue")),
            playlists = listOf(TidalPlaylist("p", "Blue Mix")),
        )

        val top = topSearchResults(result, "blue", limit = 6)
        assertEquals(5, top.size)
        assertEquals(listOf("track:t1", "album:a", "artist:ar", "track:t2", "playlist:p"), top.map { it.id })
    }

    private fun track(id: String, title: String) = TidalTrack(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
    )
}
