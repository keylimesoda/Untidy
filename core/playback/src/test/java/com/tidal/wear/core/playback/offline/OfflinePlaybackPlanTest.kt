package com.tidal.wear.core.playback.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePlaybackPlanTest {
    @Test
    fun `unmarked track may resolve for streaming`() {
        assertEquals(
            OfflinePlaybackPlan.Stream,
            offlinePlaybackPlan(downloadedMarker = false, descriptorPresent = false, cacheReady = false),
        )
    }

    @Test
    fun `legacy downloaded marker requires explicit descriptor repair instead of streaming`() {
        assertEquals(
            OfflinePlaybackPlan.RepairDescriptor,
            offlinePlaybackPlan(downloadedMarker = true, descriptorPresent = false, cacheReady = true),
        )
    }

    @Test
    fun `downloaded descriptor with missing cache requires repair`() {
        assertEquals(
            OfflinePlaybackPlan.RepairCache,
            offlinePlaybackPlan(downloadedMarker = true, descriptorPresent = true, cacheReady = false),
        )
    }

    @Test
    fun `completed descriptor and cache stays local regardless of later streaming quality`() {
        assertEquals(
            OfflinePlaybackPlan.Local,
            offlinePlaybackPlan(downloadedMarker = true, descriptorPresent = true, cacheReady = true),
        )
    }
}
