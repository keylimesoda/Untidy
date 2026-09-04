package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack

/**
 * Describes the in-memory queue window used by the manifest backend.
 *
 * Playback URLs are deliberately not part of this model.  A queue is prepared with exactly its
 * selected track, then the backend resolves and appends at most one following track at a time.
 */
internal data class PlaybackQueueWindow(
    val tracks: List<TidalTrack>,
    val startIndex: Int,
) {
    val initialTrack: TidalTrack get() = tracks[startIndex]

    /** The only manifest that may block the initial [androidx.media3.common.Player.prepare]. */
    val initialResolutionTracks: List<TidalTrack> get() = listOf(initialTrack)

    /** Maps the Media3 playlist position back to the logical queue position. */
    fun queueIndexForPlayerIndex(playerIndex: Int): Int = startIndex + playerIndex

    fun trackForPlayerIndex(playerIndex: Int): TidalTrack? =
        tracks.getOrNull(queueIndexForPlayerIndex(playerIndex))

    /** A single bounded look-ahead item, or null at the end of the queue. */
    fun lookAheadForPlayerIndex(playerIndex: Int): TidalTrack? =
        trackForPlayerIndex(playerIndex + 1)
}

internal object PlaybackQueueResolutionPolicy {
    fun normalize(tracks: List<TidalTrack>): List<TidalTrack> = tracks.filter { it.id.isNotBlank() }

    fun window(tracks: List<TidalTrack>, requestedStartIndex: Int): PlaybackQueueWindow? {
        val playableTracks = normalize(tracks)
        if (playableTracks.isEmpty()) return null
        return PlaybackQueueWindow(
            tracks = playableTracks,
            startIndex = requestedStartIndex.coerceIn(0, playableTracks.lastIndex),
        )
    }
}

/** Queue decisions that do not depend on ExoPlayer's intentionally short ready timeline. */
internal object LogicalQueueCommandPolicy {
    fun nextIndex(currentIndex: Int, queueSize: Int): Int? =
        (currentIndex + 1).takeIf { it in 0 until queueSize }

    fun previousIndex(currentIndex: Int, queueSize: Int): Int? =
        (currentIndex - 1).takeIf { it in 0 until queueSize }

    /**
     * Controllers can only see the ready Media3 window. Convert that position relative to the
     * visible player index into the complete logical queue before handling the request.
     */
    fun queueIndexForPlayerSeek(
        currentQueueIndex: Int,
        currentPlayerIndex: Int,
        requestedPlayerIndex: Int,
        queueSize: Int,
    ): Int? = (currentQueueIndex + requestedPlayerIndex - currentPlayerIndex)
        .takeIf { it in 0 until queueSize }

    fun retryDelayMs(attempt: Int): Long = when (attempt.coerceAtLeast(0)) {
        0 -> 0L
        1 -> 500L
        else -> 1_500L
    }
}

/** A manifest is valid only for the queue generation that requested it. */
internal fun shouldCancelManifestRequest(requestGeneration: Long, activeGeneration: Long): Boolean =
    requestGeneration != activeGeneration
