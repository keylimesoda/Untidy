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
        .putString("title:${track.id}", track.title)
        .putString("artist:${track.id}", track.artist)
        .putLong("downloadedAt:${track.id}", System.currentTimeMillis())
        .apply()
}

fun Context.isOfflineTrackDownloaded(trackId: String): Boolean =
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .getBoolean("$DOWNLOADED_PREFIX$trackId", false)

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
