@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * MediaSession sees the direct backend's short ready timeline, while Untidy owns a complete
 * logical queue. Forward transport commands through that logical queue before they reach the
 * wrapped player so notification, Assistant, and external-controller commands stay correct.
 */
@Suppress("DEPRECATION") // Legacy controllers still issue the window/next transport variants.
internal class LogicalQueueForwardingPlayer(
    player: Player,
    private val queueSize: () -> Int,
    private val queueIndex: () -> Int,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeekToQueueIndex: (index: Int, positionMs: Long) -> Unit,
) : ForwardingPlayer(player) {
    override fun hasNextMediaItem(): Boolean =
        LogicalQueueCommandPolicy.nextIndex(queueIndex(), queueSize()) != null

    @Deprecated("Use hasNextMediaItem")
    override fun hasNext(): Boolean = hasNextMediaItem()

    @Deprecated("Use hasNextMediaItem")
    override fun hasNextWindow(): Boolean = hasNextMediaItem()

    override fun hasPreviousMediaItem(): Boolean =
        LogicalQueueCommandPolicy.previousIndex(queueIndex(), queueSize()) != null

    override fun seekToNextMediaItem() = onNext()

    @Deprecated("Use seekToNextMediaItem")
    override fun seekToNextWindow() = onNext()

    override fun seekToNext() = onNext()

    @Deprecated("Use seekToNextMediaItem")
    override fun next() = onNext()

    override fun seekToPreviousMediaItem() = onPrevious()

    @Deprecated("Use seekToPreviousMediaItem")
    override fun seekToPreviousWindow() = onPrevious()

    override fun seekToPrevious() = onPrevious()

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val target = LogicalQueueCommandPolicy.queueIndexForPlayerSeek(
            currentQueueIndex = queueIndex(),
            currentPlayerIndex = wrappedPlayer.currentMediaItemIndex,
            requestedPlayerIndex = mediaItemIndex,
            queueSize = queueSize(),
        ) ?: return
        onSeekToQueueIndex(target, positionMs)
    }
}
