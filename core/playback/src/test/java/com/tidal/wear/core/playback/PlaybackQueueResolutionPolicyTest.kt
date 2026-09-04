package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueResolutionPolicyTest {
    @Test
    fun initialResolutionIsBoundedToTheSelectedTrackRegardlessOfQueueLength() {
        val window = PlaybackQueueResolutionPolicy.window((0 until 100).map(::track), requestedStartIndex = 42)!!

        assertEquals(listOf("track-42"), window.initialResolutionTracks.map { it.id })
        assertEquals("track-43", window.lookAheadForPlayerIndex(0)?.id)
    }

    @Test
    fun playerIndicesMapBackToTheOriginalQueueForMediaSessionMetadata() {
        val window = PlaybackQueueResolutionPolicy.window((0 until 5).map(::track), requestedStartIndex = 2)!!

        assertEquals(2, window.queueIndexForPlayerIndex(0))
        assertEquals("track-2", window.trackForPlayerIndex(0)?.id)
        assertEquals(4, window.queueIndexForPlayerIndex(2))
        assertEquals("track-4", window.trackForPlayerIndex(2)?.id)
        assertNull(window.lookAheadForPlayerIndex(2))
    }

    @Test
    fun blankIdsAreExcludedBeforeStartIndexIsBounded() {
        val window = PlaybackQueueResolutionPolicy.window(
            listOf(track(0), track(1, id = ""), track(2), track(3)),
            requestedStartIndex = 3,
        )!!

        assertEquals("track-3", window.initialTrack.id)
        assertEquals(2, window.startIndex)
    }

    @Test
    fun logicalCommandsUseTheFullQueueRatherThanTheShortReadyTimeline() {
        assertEquals(8, LogicalQueueCommandPolicy.nextIndex(currentIndex = 7, queueSize = 10))
        assertEquals(6, LogicalQueueCommandPolicy.previousIndex(currentIndex = 7, queueSize = 10))
        assertEquals(
            8,
            LogicalQueueCommandPolicy.queueIndexForPlayerSeek(
                currentQueueIndex = 7,
                currentPlayerIndex = 0,
                requestedPlayerIndex = 1,
                queueSize = 10,
            ),
        )
        assertNull(LogicalQueueCommandPolicy.nextIndex(currentIndex = 9, queueSize = 10))
    }

    @Test
    fun recoveryBackoffIsBoundedAndDeterministic() {
        assertEquals(0L, LogicalQueueCommandPolicy.retryDelayMs(0))
        assertEquals(500L, LogicalQueueCommandPolicy.retryDelayMs(1))
        assertEquals(1_500L, LogicalQueueCommandPolicy.retryDelayMs(2))
        assertEquals(1_500L, LogicalQueueCommandPolicy.retryDelayMs(99))
    }

    @Test
    fun obsoleteManifestRequestsAreCancelledWhenTheQueueGenerationChanges() {
        assertEquals(false, shouldCancelManifestRequest(requestGeneration = 4, activeGeneration = 4))
        assertEquals(true, shouldCancelManifestRequest(requestGeneration = 4, activeGeneration = 5))
    }

    private fun track(index: Int, id: String = "track-$index") = TidalTrack(
        id = id,
        title = "Track $index",
        artist = "Artist",
        album = "Album",
    )
}
