package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack
import org.junit.Assert.*
import org.junit.Test

class PlaybackRequestStatusTest {
    @Test fun failedManifestRetainsExactQueueForRetryAndNextRequestClearsError() {
        val album = listOf(
            TidalTrack("1", "First", "Artist", "Album"),
            TidalTrack("2", "Second", "Artist", "Album"),
        )
        PlaybackRequestStatus.begin(album[1], album, 1)
        assertTrue(PlaybackRequestStatus.state.value.loading)
        PlaybackRequestStatus.fail("Failed")
        val failed = PlaybackRequestStatus.state.value
        assertFalse(failed.loading)
        assertEquals(album, failed.queue)
        assertEquals(1, failed.index)
        assertEquals(album[1], failed.track)
        PlaybackRequestStatus.begin(album[1], album, 1)
        assertNull(PlaybackRequestStatus.state.value.error)
        PlaybackRequestStatus.ready()
        assertFalse(PlaybackRequestStatus.state.value.loading)
    }

    @Test fun apiResponseDetailsAreNotExposedInRecoveryMessage() {
        assertEquals("Couldn't start playback. Check your connection and retry.",
            playbackFailureMessage(java.io.IOException("HTTP 401 private response details")))
        assertTrue(playbackFailureMessage(java.io.IOException("Downloaded track needs repair: missing manifest"))
            .contains("repair"))
    }
}
