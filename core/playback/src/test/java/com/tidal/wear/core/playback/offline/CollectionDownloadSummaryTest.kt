package com.tidal.wear.core.playback.offline

import com.tidal.wear.core.model.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionDownloadSummaryTest {
    @Test
    fun `counts playable distinct downloaded and failed tracks`() {
        val tracks = listOf(
            track("1"),
            track("2"),
            track("2"),
            track("3"),
            track(""),
        )

        val summary = collectionDownloadSummary(
            tracks = tracks,
            isDownloaded = { it == "1" },
            isFailed = { it == "3" },
        )

        assertEquals(3, summary.playableCount)
        assertEquals(1, summary.downloadedCount)
        assertEquals(1, summary.failedCount)
    }

    @Test
    fun `full download excludes failed tracks`() {
        val summary = CollectionDownloadSummary(
            playableCount = 2,
            downloadedCount = 2,
            failedCount = 1,
        )

        assertEquals(false, summary.isFullyDownloaded())
        assertEquals(true, summary.hasFailures())
    }

    private fun track(id: String): TidalTrack = TidalTrack(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
    )
}
