package com.tidal.wear.core.playback.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineUnitPresentationTest {
    @Test
    fun `empty collection is unavailable for album unit`() {
        val presentation = CollectionDownloadSummary(playableCount = 0, downloadedCount = 0)
            .offlineUnitPresentation(OfflineUnit.Album)

        assertEquals("Offline unavailable", presentation.label)
        assertEquals("Album has no playable tracks", presentation.detail)
        assertEquals("Offline unavailable", presentation.message)
    }

    @Test
    fun `not downloaded collection is honest in release`() {
        val presentation = CollectionDownloadSummary(playableCount = 8, downloadedCount = 0)
            .offlineUnitPresentation(OfflineUnit.Playlist)

        assertEquals("Download unavailable", presentation.label)
        assertEquals("Not in this release", presentation.detail)
        assertEquals("Downloads unavailable in this release", presentation.message)
        assertEquals(false, presentation.canStartDownload)
        assertEquals(false, presentation.canRemoveLocalCopy)
    }

    @Test
    fun `not downloaded collection names start actions when downloads become available`() {
        val summary = CollectionDownloadSummary(playableCount = 8, downloadedCount = 0)

        val album = summary.offlineUnitPresentation(OfflineUnit.Album, downloadsAvailable = true)
        val playlist = summary.offlineUnitPresentation(OfflineUnit.Playlist, downloadsAvailable = true)

        assertEquals("Download album", album.label)
        assertEquals("Download playlist", playlist.label)
        assertEquals("Ready to save on this watch", album.detail)
        assertEquals(true, album.canStartDownload)
        assertEquals(true, playlist.canStartDownload)
    }

    @Test
    fun `fully downloaded collection uses local copy language`() {
        val presentation = CollectionDownloadSummary(playableCount = 3, downloadedCount = 3)
            .offlineUnitPresentation(OfflineUnit.Album)

        assertEquals("Downloaded 3/3", presentation.label)
        assertEquals("All local copies on watch", presentation.detail)
        assertEquals("Downloaded tracks are on watch", presentation.message)
        assertEquals(false, presentation.canStartDownload)
        assertEquals(true, presentation.canRemoveLocalCopy)
    }

    @Test
    fun `downloaded track uses compact single unit label`() {
        val presentation = CollectionDownloadSummary(playableCount = 1, downloadedCount = 1)
            .offlineUnitPresentation(OfflineUnit.Track)

        assertEquals("Downloaded", presentation.label)
        assertEquals("Local copy on watch", presentation.detail)
        assertEquals(true, presentation.canRemoveLocalCopy)
    }

    @Test
    fun `partial collection uses local copy language`() {
        val presentation = CollectionDownloadSummary(playableCount = 5, downloadedCount = 2)
            .offlineUnitPresentation(OfflineUnit.Playlist)

        assertEquals("Partial 2/5", presentation.label)
        assertEquals("Local copies on watch", presentation.detail)
        assertEquals("Partial local copies on watch", presentation.message)
        assertEquals(false, presentation.canStartDownload)
        assertEquals(true, presentation.canRemoveLocalCopy)
    }

    @Test
    fun `failed collection preserves retry copy`() {
        val presentation = CollectionDownloadSummary(playableCount = 4, downloadedCount = 1, failedCount = 2)
            .offlineUnitPresentation(OfflineUnit.Album)

        assertEquals("2 failed", presentation.label)
        assertEquals("Partial 1/4 · tap to retry later", presentation.detail)
        assertEquals("Failed tracks can retry in downloads", presentation.message)
        assertEquals(false, presentation.canStartDownload)
        assertEquals(true, presentation.canRemoveLocalCopy)
    }
}
