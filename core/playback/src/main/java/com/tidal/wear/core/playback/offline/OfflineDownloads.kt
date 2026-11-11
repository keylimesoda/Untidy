package com.tidal.wear.core.playback.offline

import android.content.Context
import com.tidal.wear.core.model.TidalTrack
import org.json.JSONArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class DownloadedTrackSummary(
    val id: String,
    val title: String,
    val artist: String,
    val downloadedAt: Long,
    val metadata: TidalTrack? = null,
) {
    fun toTrack(): TidalTrack = metadata ?: TidalTrack(id = id, title = title, artist = artist, album = "Downloaded")
}

/** The authorized DASH descriptor that populated a completed local cache. */
internal data class OfflinePlaybackDescriptor(
    val trackId: String,
    val manifestUri: String,
    val format: String,
    val presentation: String,
    val previewReason: String?,
)

/** Kept pure so the critical no-stream fallback rule is JVM-testable. */
internal enum class OfflinePlaybackPlan { Stream, Local, RepairDescriptor, RepairCache }

internal fun offlinePlaybackPlan(
    downloadedMarker: Boolean,
    descriptorPresent: Boolean,
    cacheReady: Boolean,
): OfflinePlaybackPlan = when {
    !downloadedMarker -> OfflinePlaybackPlan.Stream
    !descriptorPresent -> OfflinePlaybackPlan.RepairDescriptor
    !cacheReady -> OfflinePlaybackPlan.RepairCache
    else -> OfflinePlaybackPlan.Local
}

data class CollectionDownloadSummary(
    val playableCount: Int,
    val downloadedCount: Int,
    val failedCount: Int = 0,
    /** Tracks persisted by WorkManager but not yet saved locally. */
    val activeCount: Int = 0,
)

fun CollectionDownloadSummary.hasFailures(): Boolean = failedCount > 0

fun CollectionDownloadSummary.isFullyDownloaded(): Boolean =
    playableCount > 0 && downloadedCount >= playableCount && failedCount == 0

fun CollectionDownloadSummary.isPartiallyDownloaded(): Boolean =
    playableCount > 0 && downloadedCount > 0 && !isFullyDownloaded()

fun Context.collectionDownloadSummary(tracks: List<TidalTrack>): CollectionDownloadSummary =
    collectionDownloadSummary(
        tracks = tracks,
        isDownloaded = { id -> isOfflineTrackDownloaded(id) && !isOfflineTrackDownloadRepairRequired(id) },
        isFailed = { id -> isOfflineTrackDownloadFailed(id) || isOfflineTrackDownloadRepairRequired(id) },
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
                metadata = offlineTrackMetadata(id),
            )
        }
        .sortedByDescending { it.downloadedAt }
}

fun Context.markOfflineTrackDownloaded(track: TidalTrack) = markOfflineTrackDownloaded(track, null)

internal fun Context.markOfflineTrackDownloaded(track: TidalTrack, descriptor: OfflinePlaybackDescriptor?) {
    val record = track.toOfflineDownloadRecord() ?: return
    synchronized(offlineDownloadStateLock) {
        if (isOfflineTrackDownloadRemovalRequestedLocked(record.id)) return
        saveOfflineTrackMetadata(track)
        val editor = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$DOWNLOADED_PREFIX${record.id}", true)
            .remove("repair-required:${record.id}")
            .remove("$FAILED_PREFIX${record.id}")
            .putString("title:${record.id}", record.title)
            .putString("artist:${record.id}", record.artist)
            .putLong("downloadedAt:${record.id}", System.currentTimeMillis())
        if (descriptor != null && descriptor.trackId == record.id) {
            editor
                .putString("$OFFLINE_MANIFEST_URI_PREFIX${record.id}", descriptor.manifestUri)
                .putString("$OFFLINE_FORMAT_PREFIX${record.id}", descriptor.format)
                .putString("$OFFLINE_PRESENTATION_PREFIX${record.id}", descriptor.presentation)
                .putString("$OFFLINE_PREVIEW_REASON_PREFIX${record.id}", descriptor.previewReason)
        }
        editor.apply()
    }
}

fun Context.markOfflineTracksDownloaded(tracks: List<TidalTrack>): Int {
    val records = tracks.toOfflineDownloadRecords()
    if (records.isEmpty()) return 0
    val now = System.currentTimeMillis()
    val editor = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
    records.forEach { record ->
        editor
            .putBoolean("$DOWNLOADED_PREFIX${record.id}", true)
            .remove("$FAILED_PREFIX${record.id}")
            .putString("title:${record.id}", record.title)
            .putString("artist:${record.id}", record.artist)
            .putLong("downloadedAt:${record.id}", now)
    }
    editor.apply()
    return records.size
}

internal data class OfflineDownloadRecord(
    val id: String,
    val title: String,
    val artist: String,
)

internal fun List<TidalTrack>.toOfflineDownloadRecords(): List<OfflineDownloadRecord> =
    mapNotNull { it.toOfflineDownloadRecord() }.distinctBy { it.id }

private fun TidalTrack.toOfflineDownloadRecord(): OfflineDownloadRecord? {
    val playableId = id.takeIf(String::isNotBlank) ?: return null
    return OfflineDownloadRecord(
        id = playableId,
        title = title.ifBlank { "Downloaded track" },
        artist = artist,
    )
}

fun Context.markOfflineTrackDownloadFailed(trackId: String) {
    if (trackId.isBlank()) return
    synchronized(offlineDownloadStateLock) {
        if (!isOfflineTrackDownloadRemovalRequestedLocked(trackId)) {
            getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("$FAILED_PREFIX$trackId", true)
                .apply()
        }
    }
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
 * Old versions stored only the marker and cache bytes. Keep those bytes intact, but require a
 * deliberate redownload before replay: obtaining a fresh stream would not be offline playback.
 */
fun Context.isOfflineTrackDownloadRepairRequired(trackId: String): Boolean =
    isOfflineTrackDownloaded(trackId) && (offlinePlaybackDescriptor(trackId) == null ||
        !offlineTrackCacheDir(trackId).isDirectory ||
        getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE).getBoolean("repair-required:$trackId", false))

internal fun Context.markOfflineTrackRepairRequired(trackId: String) {
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean("repair-required:$trackId", true).apply()
}

internal fun Context.offlinePlaybackDescriptor(trackId: String): OfflinePlaybackDescriptor? {
    if (trackId.isBlank()) return null
    val prefs = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
    val manifestUri = prefs.getString("$OFFLINE_MANIFEST_URI_PREFIX$trackId", null)
        ?.takeIf { it.isNotBlank() } ?: return null
    val format = prefs.getString("$OFFLINE_FORMAT_PREFIX$trackId", null)
        ?.takeIf { it.isNotBlank() } ?: return null
    return OfflinePlaybackDescriptor(
        trackId = trackId,
        manifestUri = manifestUri,
        format = format,
        presentation = prefs.getString("$OFFLINE_PRESENTATION_PREFIX$trackId", null)
            ?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
        previewReason = prefs.getString("$OFFLINE_PREVIEW_REASON_PREFIX$trackId", null)
            ?.takeIf { it.isNotBlank() },
    )
}

/**
 * Removes only Untidy-local offline state for a downloaded track.
 * This never mutates TIDAL library, playlists, playlist membership, or remote assets.
 */
fun Context.removeOfflineTrackDownload(trackId: String): Boolean {
    if (trackId.isBlank()) return false
    markOfflineTrackDownloadRemovalRequested(trackId)
    OfflineDownloadScheduler(this).cancel(trackId)
    return removeOfflineTrackDownloadState(trackId)
}

/**
 * Schedules a replacement for a legacy/incomplete local copy without removing its cache first.
 * The old descriptor and bytes remain available for diagnostics until a new download completes.
 */
suspend fun Context.repairOfflineTrackDownload(track: TidalTrack): OfflineBatchScheduleResult {
    if (track.id.isBlank() || !isOfflineTrackDownloadRepairRequired(track.id)) {
        return OfflineBatchScheduleResult(requested = 0, scheduled = 0)
    }
    return OfflineDownloadScheduler(this).enqueue(listOf(track))
}

/**
 * Removes all Untidy-local downloaded track state and cache bytes from this watch.
 * This never mutates TIDAL library, playlists, playlist membership, or remote assets.
 */
fun Context.removeAllOfflineTrackDownloads(): Boolean {
    val queuedIds = tombstoneAndClearQueuedOfflineDownloads()
    OfflineDownloadScheduler(this).cancelAll()
    val ids = queuedIds + readOfflineDownloadedTracks().map { it.id }
    return ids.distinct().map(::removeOfflineTrackDownloadState).all { it }
}

fun Context.offlineDownloadsStorageBytes(): Long =
    readOfflineDownloadedTracks()
        .sumOf { offlineTrackCacheDir(it.id).directorySizeBytes() }

fun Context.offlineTrackCacheDir(trackId: String): File =
    File(filesDir, "offline-downloads/cache-$trackId")

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
private const val REMOVAL_REQUESTED_PREFIX = "removal-requested:"
private const val OFFLINE_MANIFEST_URI_PREFIX = "offline-manifest-uri:"
private const val OFFLINE_FORMAT_PREFIX = "offline-format:"
private const val OFFLINE_PRESENTATION_PREFIX = "offline-presentation:"
private const val OFFLINE_PREVIEW_REASON_PREFIX = "offline-preview-reason:"

internal fun Context.markOfflineTrackDownloadRemovalRequested(trackId: String) {
    synchronized(offlineDownloadStateLock) {
        getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$REMOVAL_REQUESTED_PREFIX$trackId", true)
            .apply()
    }
}

internal fun Context.markOfflineTrackDownloadQueued(record: OfflineDownloadRecord) {
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("title:${record.id}", record.title)
        .putString("artist:${record.id}", record.artist)
        .apply()
}

internal fun Context.offlineTrackDownloadMetadata(trackId: String): Pair<String, String> {
    val prefs = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
    return (
        prefs.getString("title:$trackId", null)?.takeIf { it.isNotBlank() } ?: "Downloaded track"
    ) to prefs.getString("artist:$trackId", null).orEmpty()
}

internal fun Context.saveOfflineTrackMetadata(track: TidalTrack) {
    // Queue workers have a minimal title/artist record; never overwrite richer saved context.
    val existing = offlineTrackMetadata(track.id)
    if (existing != null && track.albumId.isBlank() && track.artistId.isBlank() && track.album == "Downloaded") return
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
        .putString("metadata:${track.id}", Json.encodeToString(track)).apply()
}

internal fun Context.offlineTrackMetadata(trackId: String): TidalTrack? = runCatching {
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE).getString("metadata:$trackId", null)
        ?.let { Json.decodeFromString<TidalTrack>(it) }?.takeIf { it.id == trackId }
}.getOrNull()

internal fun Context.clearOfflineTrackDownloadRemovalRequested(trackId: String) {
    synchronized(offlineDownloadStateLock) {
        getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$REMOVAL_REQUESTED_PREFIX$trackId")
            .apply()
    }
}

internal fun Context.isOfflineTrackDownloadRemovalRequested(trackId: String): Boolean =
    synchronized(offlineDownloadStateLock) { isOfflineTrackDownloadRemovalRequestedLocked(trackId) }

internal fun Context.enqueueOfflineDownloadRecords(records: List<OfflineDownloadRecord>): List<OfflineDownloadRecord> =
    synchronized(offlineDownloadStateLock) {
        val queued = readQueuedOfflineDownloadRecordsLocked().toMutableList()
        val additions = OfflineDownloadQueuePolicy.appendInOrder(
            existing = queued,
            candidates = records,
            // A legacy marker without its descriptor is deliberately repairable. Do not delete
            // its old bytes here; DashDownloader can reuse compatible cached spans.
            isAlreadyDownloaded = { id -> isOfflineTrackDownloaded(id) && !isOfflineTrackDownloadRepairRequired(id) },
        )
        additions.forEach { record ->
            clearOfflineTrackDownloadRemovalRequested(record.id)
            clearOfflineTrackDownloadFailed(record.id)
            markOfflineTrackDownloadQueued(record)
            queued += record
        }
        writeQueuedOfflineDownloadRecordsLocked(queued)
        additions
    }

internal fun Context.readQueuedOfflineDownloadRecords(): List<OfflineDownloadRecord> =
    synchronized(offlineDownloadStateLock) { readQueuedOfflineDownloadRecordsLocked() }

internal fun Context.nextQueuedOfflineDownloadRecord(): OfflineDownloadRecord? =
    synchronized(offlineDownloadStateLock) { readQueuedOfflineDownloadRecordsLocked().firstOrNull() }

internal fun Context.removeQueuedOfflineDownloadRecord(trackId: String) {
    synchronized(offlineDownloadStateLock) {
        val mutation = OfflineDownloadQueuePolicy.tombstoneAndRemove(
            records = readQueuedOfflineDownloadRecordsLocked(),
            ids = setOf(trackId),
        )
        writeQueuedOfflineDownloadRecordsLocked(mutation.remaining)
    }
}

/** Tombstones the active head and every queued record before cancellation can race with them. */
internal fun Context.tombstoneAndClearQueuedOfflineDownloads(): List<String> =
    synchronized(offlineDownloadStateLock) {
        val records = readQueuedOfflineDownloadRecordsLocked()
        val mutation = OfflineDownloadQueuePolicy.tombstoneAndRemove(
            records = records,
            ids = records.mapTo(mutableSetOf()) { it.id },
        )
        val ids = mutation.tombstonedIds
        val editor = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { editor.putBoolean("$REMOVAL_REQUESTED_PREFIX$it", true) }
        editor.putString(QUEUED_TRACK_IDS_KEY, JSONArray().apply { mutation.remaining.forEach { put(it.id) } }.toString()).apply()
        ids
    }

/** Clears cache and visible state, retaining the tombstone until a deliberate new enqueue. */
internal fun Context.removeOfflineTrackDownloadState(trackId: String): Boolean =
    synchronized(offlineDownloadStateLock) {
        val cacheDir = offlineTrackCacheDir(trackId)
        val cacheRemoved = !cacheDir.exists() || cacheDir.deleteRecursively()
        if (!cacheRemoved) return false
        getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$DOWNLOADED_PREFIX$trackId")
            .remove("repair-required:$trackId")
            .remove("title:$trackId")
            .remove("artist:$trackId")
            .remove("downloadedAt:$trackId")
            .remove("metadata:$trackId")
            .remove("$FAILED_PREFIX$trackId")
            .remove("$OFFLINE_MANIFEST_URI_PREFIX$trackId")
            .remove("$OFFLINE_FORMAT_PREFIX$trackId")
            .remove("$OFFLINE_PRESENTATION_PREFIX$trackId")
            .remove("$OFFLINE_PREVIEW_REASON_PREFIX$trackId")
            .apply()
        true
    }

private fun Context.readQueuedOfflineDownloadRecordsLocked(): List<OfflineDownloadRecord> {
    val prefs = getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
    val ids = runCatching { JSONArray(prefs.getString(QUEUED_TRACK_IDS_KEY, "[]")) }.getOrDefault(JSONArray())
    return buildList {
        repeat(ids.length()) { index ->
            val id = ids.optString(index).takeIf(String::isNotBlank) ?: return@repeat
            val (title, artist) = offlineTrackDownloadMetadata(id)
            add(OfflineDownloadRecord(id, title, artist))
        }
    }.distinctBy { it.id }
}

private fun Context.writeQueuedOfflineDownloadRecordsLocked(records: List<OfflineDownloadRecord>) {
    val ids = JSONArray().apply { records.forEach { put(it.id) } }
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(QUEUED_TRACK_IDS_KEY, ids.toString())
        .apply()
}

private fun Context.isOfflineTrackDownloadRemovalRequestedLocked(trackId: String): Boolean =
    getSharedPreferences(OFFLINE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        .getBoolean("$REMOVAL_REQUESTED_PREFIX$trackId", false)

private val offlineDownloadStateLock = Any()
private const val QUEUED_TRACK_IDS_KEY = "queued-track-ids"

internal object OfflineDownloadQueuePolicy {
    /** Appends only new, non-local records, retaining the caller's FIFO ordering. */
    fun appendInOrder(
        existing: List<OfflineDownloadRecord>,
        candidates: List<OfflineDownloadRecord>,
        isAlreadyDownloaded: (String) -> Boolean,
    ): List<OfflineDownloadRecord> {
        val knownIds = existing.mapTo(mutableSetOf()) { it.id }
        return candidates.filter { record -> !isAlreadyDownloaded(record.id) && knownIds.add(record.id) }
    }

    /** The returned IDs must be tombstoned before the remaining queue is persisted. */
    fun tombstoneAndRemove(records: List<OfflineDownloadRecord>, ids: Set<String>): OfflineQueueRemoval =
        OfflineQueueRemoval(
            tombstonedIds = records.asSequence().map { it.id }.filter(ids::contains).distinct().toList(),
            remaining = records.filterNot { it.id in ids },
        )
}

internal data class OfflineQueueRemoval(
    val tombstonedIds: List<String>,
    val remaining: List<OfflineDownloadRecord>,
)
