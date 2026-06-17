package com.tidal.wear.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransitionPolicyTest {
    @Test
    fun longCatalogTracksDoNotAdvanceQueueOnVeryEarlyMediaEnd() {
        assertTrue(
            shouldHoldQueueAdvanceOnEarlyMediaEnd(
                elapsedMs = 30_000L,
                durationMs = 240_000L,
            ),
        )
    }

    @Test
    fun longCatalogTracksAdvanceWhenEndIsNearCatalogDuration() {
        assertFalse(
            shouldHoldQueueAdvanceOnEarlyMediaEnd(
                elapsedMs = 205_000L,
                durationMs = 240_000L,
            ),
        )
    }

    @Test
    fun shortTracksAndPreviewLengthTracksCanStillAdvance() {
        assertFalse(
            shouldHoldQueueAdvanceOnEarlyMediaEnd(
                elapsedMs = 25_000L,
                durationMs = 59_000L,
            ),
        )
    }

    @Test
    fun negativeElapsedTimeIsTreatedAsImmediateEnd() {
        assertTrue(
            shouldHoldQueueAdvanceOnEarlyMediaEnd(
                elapsedMs = -1_000L,
                durationMs = 180_000L,
            ),
        )
    }
}
