@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.asFlow
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.settings.downloadOverLteEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/** A single persistent Wear download lane backed by a deterministic FIFO track queue. */
class OfflineDownloadScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueue(tracks: List<TidalTrack>): OfflineBatchScheduleResult {
        val records = tracks.toOfflineDownloadRecords()
        val scheduled = appContext.enqueueOfflineDownloadRecords(records)
        if (scheduled.isNotEmpty()) {
            workManager.enqueueUniqueWork(
                OfflineDownloadWorkPolicy.QUEUE_WORK_NAME,
                // A trailing worker closes the enqueue-vs-finish race without interrupting I/O.
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                queueRequest(downloadOverLteEnabled = currentDownloadOverLteEnabled()),
            )
        }
        return OfflineBatchScheduleResult(requested = records.size, scheduled = scheduled.size)
    }

    /**
     * Rebuilds the queue request after the LTE preference changes.
     *
     * WorkManager constraints are immutable once a request is enqueued. The
     * actual records are persisted separately, so replacing this one queue
     * request cannot lose tracks; a worker that is in-flight will finish (or
     * observe cancellation) against that same durable queue before the new
     * request takes over.
     */
    suspend fun refreshQueuedWorkConstraints() {
        if (appContext.readQueuedOfflineDownloadRecords().isEmpty()) return
        workManager.cancelUniqueWork(OfflineDownloadWorkPolicy.QUEUE_WORK_NAME)
        workManager.enqueueUniqueWork(
            OfflineDownloadWorkPolicy.QUEUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            queueRequest(downloadOverLteEnabled = appContext.downloadOverLteEnabled.first()),
        )
    }

    fun observeSummary(tracks: List<TidalTrack>): Flow<CollectionDownloadSummary> {
        val trackIds = tracks.mapNotNull { it.id.takeIf(String::isNotBlank) }.distinct().toSet()
        return workManager.getWorkInfosByTagLiveData(OfflineDownloadWorkPolicy.ALL_DOWNLOADS_TAG)
            .asFlow()
            .map {
                val activeIds = appContext.readQueuedOfflineDownloadRecords()
                    .asSequence()
                    .map { it.id }
                    .filter(trackIds::contains)
                    .toSet()
                appContext.collectionDownloadSummary(tracks).copy(activeCount = activeIds.size)
            }
    }

    fun observeActiveTracks(): Flow<List<OfflineQueuedTrack>> =
        workManager.getWorkInfosByTagLiveData(OfflineDownloadWorkPolicy.ALL_DOWNLOADS_TAG)
            .asFlow()
            .map { work ->
                val runningId = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    ?.progress
                    ?.getString(OfflineDownloadWorkPolicy.TRACK_ID)
                appContext.readQueuedOfflineDownloadRecords().map { record ->
                    OfflineQueuedTrack(
                        id = record.id,
                        title = record.title,
                        artist = record.artist,
                        state = if (record.id == runningId) WorkInfo.State.RUNNING else WorkInfo.State.ENQUEUED,
                    )
                }
            }

    /** Removes one track from the persisted queue. The shared worker continues with the next item. */
    fun cancel(trackId: String) {
        if (trackId.isNotBlank()) appContext.removeQueuedOfflineDownloadRecord(trackId)
    }

    fun cancelAll() {
        appContext.tombstoneAndClearQueuedOfflineDownloads()
        workManager.cancelUniqueWork(OfflineDownloadWorkPolicy.QUEUE_WORK_NAME)
    }

    private fun queueRequest(downloadOverLteEnabled: Boolean) = OneTimeWorkRequestBuilder<OfflineDownloadQueueWorker>()
        .setConstraints(OfflineDownloadWorkPolicy.constraints(downloadOverLteEnabled))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag(OfflineDownloadWorkPolicy.ALL_DOWNLOADS_TAG)
        .build()

    private fun currentDownloadOverLteEnabled(): Boolean = runBlocking(Dispatchers.IO) {
        appContext.downloadOverLteEnabled.first()
    }
}

data class OfflineBatchScheduleResult(val requested: Int, val scheduled: Int)

data class OfflineQueuedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val state: WorkInfo.State,
)

class OfflineDownloadQueueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        while (!isStopped) {
            val record = applicationContext.nextQueuedOfflineDownloadRecord() ?: return Result.success()
            if (applicationContext.isOfflineTrackDownloadRemovalRequested(record.id)) {
                applicationContext.removeQueuedOfflineDownloadRecord(record.id)
                applicationContext.removeOfflineTrackDownloadState(record.id)
                continue
            }

            setProgress(workDataOf(OfflineDownloadWorkPolicy.TRACK_ID to record.id))
            applicationContext.clearOfflineTrackDownloadFailed(record.id)
            val track = TidalTrack(id = record.id, title = record.title, artist = record.artist, album = "Downloaded")
            when (OfflineTrackDownloader(applicationContext, TidalAuthRepositoryProvider.get(applicationContext)).downloadTrack(track)) {
                OfflineDownloadResult.Downloaded -> applicationContext.removeQueuedOfflineDownloadRecord(record.id)
                OfflineDownloadResult.Skipped -> {
                    applicationContext.removeQueuedOfflineDownloadRecord(record.id)
                    if (applicationContext.isOfflineTrackDownloadRemovalRequested(record.id)) {
                        applicationContext.removeOfflineTrackDownloadState(record.id)
                    }
                }
                OfflineDownloadResult.WaitingForNetwork -> {
                    // The durable record remains queued. Retry keeps transient
                    // radio changes recoverable; changing the LTE setting
                    // calls refreshQueuedWorkConstraints() to replace the
                    // immutable WorkManager constraint.
                    return Result.retry()
                }
                is OfflineDownloadResult.Failed -> {
                    // Fail visibly; do not spin the radio retrying an unknown network/API error.
                    applicationContext.markOfflineTrackDownloadFailed(record.id)
                    applicationContext.removeQueuedOfflineDownloadRecord(record.id)
                }
            }
        }
        return Result.retry()
    }
}

internal object OfflineDownloadWorkPolicy {
    const val ALL_DOWNLOADS_TAG = "untidy-offline-download"
    const val QUEUE_WORK_NAME = "untidy-offline-download-queue"
    const val TRACK_ID = "track_id"
    fun constraints(downloadOverLteEnabled: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (downloadOverLteEnabled) NetworkType.CONNECTED else NetworkType.UNMETERED,
        )
        .setRequiresBatteryNotLow(true)
        .build()

    /** Default policy is deliberately conservative for callers that only need a static policy. */
    val constraints: Constraints = constraints(downloadOverLteEnabled = false)
    /** Per-track UI remains user-initiated, but is gated by the same LTE policy as collections. */
    val singleTrackNetworkPolicy = SingleTrackNetworkPolicy.UserInitiatedImmediate
    val activeStates = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    fun uniqueName(trackId: String) = "untidy-offline-track-$trackId"
}

/** Returns whether an immediate download is allowed by the current radio and the user policy. */
internal fun Context.hasAllowedOfflineDownloadNetwork(downloadOverLteEnabled: Boolean): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
    val network = connectivity.activeNetwork ?: return false
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
    return isOfflineDownloadNetworkAllowed(
        downloadOverLteEnabled = downloadOverLteEnabled,
        hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}

internal fun isOfflineDownloadNetworkAllowed(
    downloadOverLteEnabled: Boolean,
    hasInternet: Boolean,
    isMetered: Boolean,
): Boolean = hasInternet && (downloadOverLteEnabled || !isMetered)

internal enum class SingleTrackNetworkPolicy {
    UserInitiatedImmediate,
}
