package com.tidal.wear.core.api

import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResponsePolicyTest {
    @Test
    fun `track-only v2 results request entity supplementation`() {
        assertTrue(
            shouldSupplementLegacySearch(
                TidalSearchResult(tracks = listOf(track(artworkUrl = null))),
            ),
        )
        assertFalse(
            shouldSupplementLegacySearch(
                TidalSearchResult(
                    tracks = listOf(track(artworkUrl = null)),
                    artists = listOf(TidalArtist("artist-1", "Moby")),
                ),
            ),
        )
    }

    @Test
    fun `supplement fills track artwork and restores missing entity groups`() {
        val result = supplementSearchResult(
            primary = TidalSearchResult(tracks = listOf(track(artworkUrl = null))),
            supplement = TidalSearchResult(
                tracks = listOf(track(artworkUrl = "https://resources.tidal.com/images/cover.jpg")),
                albums = listOf(TidalAlbum("album-1", "Play", "Moby")),
                artists = listOf(TidalArtist("artist-1", "Moby")),
            ),
        )

        assertEquals("https://resources.tidal.com/images/cover.jpg", result.tracks.single().artworkUrl)
        assertEquals(listOf("Play"), result.albums.map { it.title })
        assertEquals(listOf("Moby"), result.artists.map { it.name })
    }

    @Test
    fun `v2 track uses included album artwork`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": {"type": "searchResults", "id": "moby"},
              "included": [
                {
                  "type": "tracks",
                  "id": "track-1",
                  "attributes": {"title": "Porcelain"},
                  "relationships": {
                    "albums": {"data": {"type": "albums", "id": "album-1"}},
                    "artists": {"data": {"type": "artists", "id": "artist-1"}}
                  }
                },
                {
                  "type": "albums",
                  "id": "album-1",
                  "attributes": {"title": "Play", "cover": "abc-def"}
                },
                {
                  "type": "artists",
                  "id": "artist-1",
                  "attributes": {"name": "Moby"}
                }
              ]
            }
            """.trimIndent(),
        )

        val result = parseV2Search(payload)

        assertEquals("Play", result.tracks.single().album)
        assertTrue(result.tracks.single().artworkUrl.orEmpty().contains("abc/def"))
        assertEquals(listOf("Moby"), result.artists.map { it.name })
        assertEquals(listOf("Play"), result.albums.map { it.title })
    }

    private fun track(artworkUrl: String?) = TidalTrack(
        id = "track-1",
        title = "Porcelain",
        artist = "Moby",
        album = "Play",
        artworkUrl = artworkUrl,
    )
}
