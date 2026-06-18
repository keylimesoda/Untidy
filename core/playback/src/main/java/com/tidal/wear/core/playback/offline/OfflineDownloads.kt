package com.tidal.wear.core.playback.offline

import android.content.Context
import com.tidal.wear.core.model.TidalTrack
import java.io.File

data class DownloadedTrackSummary(
    val id: String,
    val title: String,
    val artist: String,
    val downloadedAt: Long,
) {
    fun toTrack(): TidalTrack = TidalTrack(id = id, title = title, artist = artist, album = "Downloaded")
}

data class CollectionDownloadSummary(
    val playableCount: Int,
    val downloadedCount: Int,
    val failedCount: Int = 0,
)

fun CollectionDownloadSummary.hasFailures(): Boolean = failedCount > 0

fun CollectionDownloadSummary.isFullyDownloaded(): Boolean =
    playableCount > 0 && downloadedCount >= playableCount && failedCount == 0

fun CollectionDownloadSummary.isPartiallyDownloaded(): Boolean =
    playableCount > 0 && downloadedCount > 0 && !isFullyDownloaded()

fun Context.collectionDownloadSummary(tracks: List<TidalTrack>): CollectionDownloadSummary =
    collectionDownloadSummary(
        tracks = tracks,
        isDownloaded = ::isOfflineTrackDownloaded,
        isFailed = ::isOfflineTrackDownloadFailed,
    )

internal fun collectionDownloadSummary(
    tracks: List<TidalTrack>,
    isDownloaded: (String) -> Boolean,
    isFailed: (String) -> Boolean,
): CollectionDownloadSummary {
    val playableIds = tracks.mapNotNull { it.id.takeIf(String::isNotBlank) }.distinct()
    if (playableIds.isEmpty()) return CollectionDownloadSummary(playableCount = 0, downloadedCount = 0)
    return CollectionDownloadSummary(
        playableCount = playableIds.size,
        downloadedCount = playableIds.count(isDownloaded),
        failedCount = playableIds.count(isFailed),
    )
}

fun Context.readOfflineDownloadedTracks(): List<DownloadedTrackSummary> {
    val prefs = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
    return prefs.all.keys
        .filter { it.startsWith(DOWNLOADED_PREFIX) && prefs.getBoolean(it, false) }
        .mapNotNull { key ->
            val id = key.removePrefix(DOWNLOADED_PREFIX).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DownloadedTrackSummary(
                id = id,
                title = prefs.getString("title:$id", null)?.takeIf { it.isNotBlank() } ?: "Downloaded track",
                artist = prefs.getString("artist:$id", null).orEmpty(),
                downloadedAt = prefs.getLong("downloadedAt:$id", 0L),
            )
        }
        .sortedByDescending { it.downloadedAt }
}

fun Context.markOfflineTrackDownloaded(track: TidalTrack) {
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("$DOWNLOADED_PREFIX${track.id}", true)
        .remove("$FAILED_PREFIX${track.id}")
        .putString("title:${track.id}", track.title)
        .putString("artist:${track.id}", track.artist)
        .putLong("downloadedAt:${track.id}", System.currentTimeMillis())
        .apply()
}

fun Context.markOfflineTrackDownloadFailed(trackId: String) {
    if (trackId.isBlank()) return
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("$FAILED_PREFIX$trackId", true)
        .apply()
}

fun Context.clearOfflineTrackDownloadFailed(trackId: String) {
    if (trackId.isBlank()) return
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("$FAILED_PREFIX$trackId")
        .apply()
}

fun Context.isOfflineTrackDownloaded(trackId: String): Boolean =
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .getBoolean("$DOWNLOADED_PREFIX$trackId", false)

fun Context.isOfflineTrackDownloadFailed(trackId: String): Boolean =
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .getBoolean("$FAILED_PREFIX$trackId", false)

/**
 * Removes only Untidy-local offline state for a downloaded track.
 * This never mutates TIDAL library, playlists, playlist membership, or remote assets.
 */
fun Context.removeOfflineTrackDownload(trackId: String): Boolean {
    if (trackId.isBlank()) return false
    val cacheDir = offlineTrackCacheDir(trackId)
    val cacheRemoved = !cacheDir.exists() || cacheDir.deleteRecursively()
    if (!cacheRemoved) return false

    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("$DOWNLOADED_PREFIX$trackId")
        .remove("title:$trackId")
        .remove("artist:$trackId")
        .remove("downloadedAt:$trackId")
        .remove("$FAILED_PREFIX$trackId")
        .apply()
    return true
}

/**
 * Removes all Untidy-local downloaded track state and cache bytes from this watch.
 * This never mutates TIDAL library, playlists, playlist membership, or remote assets.
 */
fun Context.removeAllOfflineTrackDownloads(): Boolean =
    readOfflineDownloadedTracks().map { removeOfflineTrackDownload(it.id) }.all { it }

fun Context.offlineDownloadsStorageBytes(): Long =
    readOfflineDownloadedTracks()
        .sumOf { offlineTrackCacheDir(it.id).directorySizeBytes() }

fun Context.offlineTrackCacheDir(trackId: String): File =
    File(filesDir, "offline-proof-cachefill/cache-$trackId")

private fun File.directorySizeBytes(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}

private const val OFFLINE_DOWNLOAD_PREFS = "offline-downloads"
private const val DOWNLOADED_PREFIX = "downloaded:"
private const val FAILED_PREFIX = "failed:"
