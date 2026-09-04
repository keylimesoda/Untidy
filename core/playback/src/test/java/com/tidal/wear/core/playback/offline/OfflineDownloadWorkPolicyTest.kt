package com.tidal.wear.core.playback.offline

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadWorkPolicyTest {
    @Test
    fun `queue appends unique tracks in deterministic FIFO order`() {
        val first = OfflineDownloadRecord("one", "One", "A")
        val existing = listOf(first)
        val additions = OfflineDownloadQueuePolicy.appendInOrder(
            existing = existing,
            candidates = listOf(first, OfflineDownloadRecord("two", "Two", "B"), OfflineDownloadRecord("three", "Three", "C")),
            isAlreadyDownloaded = { it == "three" },
        )

        assertEquals(listOf("two"), additions.map { it.id })
        assertEquals("untidy-offline-download-queue", OfflineDownloadWorkPolicy.QUEUE_WORK_NAME)
    }

    @Test
    fun `collection downloads require unmetered network and expose queued states`() {
        assertEquals(NetworkType.UNMETERED, OfflineDownloadWorkPolicy.constraints.requiredNetworkType)
        assertTrue(OfflineDownloadWorkPolicy.constraints.requiresBatteryNotLow())
        assertEquals(SingleTrackNetworkPolicy.UserInitiatedImmediate, OfflineDownloadWorkPolicy.singleTrackNetworkPolicy)
        assertTrue(OfflineDownloadWorkPolicy.activeStates.any { it.name == "ENQUEUED" })
        assertTrue(OfflineDownloadWorkPolicy.activeStates.any { it.name == "RUNNING" })
    }

    @Test
    fun `LTE opt in permits any connected network while retaining battery guard`() {
        val constraints = OfflineDownloadWorkPolicy.constraints(downloadOverLteEnabled = true)

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `direct downloads allow metered network only after LTE opt in`() {
        assertTrue(
            isOfflineDownloadNetworkAllowed(
                downloadOverLteEnabled = false,
                hasInternet = true,
                isMetered = false,
            ),
        )
        assertTrue(
            isOfflineDownloadNetworkAllowed(
                downloadOverLteEnabled = true,
                hasInternet = true,
                isMetered = true,
            ),
        )
        assertTrue(
            !isOfflineDownloadNetworkAllowed(
                downloadOverLteEnabled = false,
                hasInternet = true,
                isMetered = true,
            ),
        )
    }

    @Test
    fun `removal tombstones both active head and every queued matching track before cleanup`() {
        val records = listOf(
            OfflineDownloadRecord("active", "Active", "A"),
            OfflineDownloadRecord("queued", "Queued", "B"),
            OfflineDownloadRecord("keep", "Keep", "C"),
        )

        val mutation = OfflineDownloadQueuePolicy.tombstoneAndRemove(records, setOf("active", "queued"))

        assertEquals(listOf("active", "queued"), mutation.tombstonedIds)
        assertEquals(listOf("keep"), mutation.remaining.map { it.id })
    }

    @Test
    fun `active collection is presented as background saving rather than complete`() {
        val presentation = CollectionDownloadSummary(
            playableCount = 4,
            downloadedCount = 1,
            activeCount = 3,
        ).offlineUnitPresentation(OfflineUnit.Album, downloadsAvailable = true)

        assertEquals("Saving 3 tracks", presentation.label)
        assertEquals("1/4 on watch · saving in background", presentation.detail)
        assertTrue(!presentation.canStartDownload)
    }
}
