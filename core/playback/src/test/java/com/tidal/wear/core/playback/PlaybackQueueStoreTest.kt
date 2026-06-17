package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueStoreTest {
    @Test
    fun putFiltersBlankTrackIdsAndPreservesPlayableOrder() {
        val id = PlaybackQueueStore.put(
            listOf(
                track("one"),
                track(""),
                track("two"),
                track("   "),
                track("three"),
            ),
        )

        assertEquals(listOf("one", "two", "three"), PlaybackQueueStore.get(id).map { it.id })
    }

    @Test
    fun putCapsQueuesAtOneHundredTracks() {
        val id = PlaybackQueueStore.put((1..150).map { track("track-$it") })

        val queue = PlaybackQueueStore.get(id)
        assertEquals(100, queue.size)
        assertEquals("track-1", queue.first().id)
        assertEquals("track-100", queue.last().id)
    }

    @Test
    fun payloadRoundTripPreservesTrackMetadata() {
        val payload = PlaybackQueueStore.payloadFor(
            listOf(
                track(
                    id = "one|with\nseparators",
                    title = "Title | newline\nplus spaces",
                    artist = "Artist",
                    album = "Album",
                    artworkUrl = "https://example.test/art?id=1|2",
                    durationMs = 123_456L,
                    streamUrl = "https://example.test/stream",
                ),
            ),
        )

        val restored = PlaybackQueueStore.fromPayload(payload)

        assertEquals(1, restored.size)
        assertEquals("one|with\nseparators", restored.single().id)
        assertEquals("Title | newline\nplus spaces", restored.single().title)
        assertEquals("https://example.test/art?id=1|2", restored.single().artworkUrl)
        assertEquals(123_456L, restored.single().durationMs)
        assertEquals("https://example.test/stream", restored.single().streamUrl)
    }

    @Test
    fun payloadRoundTripAlsoCapsQueuesAtOneHundredTracks() {
        val payload = PlaybackQueueStore.payloadFor((1..150).map { track("track-$it") })

        val queue = PlaybackQueueStore.fromPayload(payload)

        assertEquals(100, queue.size)
        assertEquals("track-1", queue.first().id)
        assertEquals("track-100", queue.last().id)
    }

    @Test
    fun getUnknownQueueReturnsEmptyList() {
        assertTrue(PlaybackQueueStore.get("missing").isEmpty())
    }

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist",
        album: String = "Album",
        artworkUrl: String? = null,
        durationMs: Long = 0L,
        streamUrl: String? = null,
    ) = TidalTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
    )
}
