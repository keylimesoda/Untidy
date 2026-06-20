package com.tidal.wear.core.playback.offline

sealed interface OfflineUnit {
    data object Track : OfflineUnit
    data object Album : OfflineUnit
    data object Playlist : OfflineUnit
}

data class OfflineUnitPresentation(
    val label: String,
    val detail: String,
    val message: String,
    val canStartDownload: Boolean,
    val canRemoveLocalCopy: Boolean,
)

fun CollectionDownloadSummary.offlineUnitPresentation(
    unit: OfflineUnit,
    downloadsAvailable: Boolean = false,
): OfflineUnitPresentation = when {
    playableCount <= 0 -> OfflineUnitPresentation(
        label = "Offline unavailable",
        detail = "${unit.displayName} has no playable tracks",
        message = "Offline unavailable",
        canStartDownload = false,
        canRemoveLocalCopy = false,
    )
    hasFailures() -> OfflineUnitPresentation(
        label = "$failedCount failed",
        detail = if (downloadedCount > 0) "Partial $downloadedCount/$playableCount · tap to retry later" else "Tap to retry later",
        message = "Failed tracks can retry in downloads",
        canStartDownload = downloadsAvailable,
        canRemoveLocalCopy = downloadedCount > 0,
    )
    downloadedCount <= 0 -> OfflineUnitPresentation(
        label = if (downloadsAvailable) unit.downloadActionLabel else "Download unavailable",
        detail = if (downloadsAvailable) "Ready to save on this watch" else "Not in this release",
        message = if (downloadsAvailable) "${unit.displayName} download can start" else "Downloads unavailable in this release",
        canStartDownload = downloadsAvailable,
        canRemoveLocalCopy = false,
    )
    isFullyDownloaded() -> OfflineUnitPresentation(
        label = if (unit == OfflineUnit.Track) "Downloaded" else "Downloaded $downloadedCount/$playableCount",
        detail = if (unit == OfflineUnit.Track) "Local copy on watch" else "All local copies on watch",
        message = "Downloaded tracks are on watch",
        canStartDownload = false,
        canRemoveLocalCopy = true,
    )
    else -> OfflineUnitPresentation(
        label = "Partial $downloadedCount/$playableCount",
        detail = "Local copies on watch",
        message = "Partial local copies on watch",
        canStartDownload = downloadsAvailable,
        canRemoveLocalCopy = true,
    )
}

private val OfflineUnit.displayName: String
    get() = when (this) {
        OfflineUnit.Track -> "Track"
        OfflineUnit.Album -> "Album"
        OfflineUnit.Playlist -> "Playlist"
    }

private val OfflineUnit.downloadActionLabel: String
    get() = when (this) {
        OfflineUnit.Track -> "Download"
        OfflineUnit.Album -> "Download album"
        OfflineUnit.Playlist -> "Download playlist"
    }
