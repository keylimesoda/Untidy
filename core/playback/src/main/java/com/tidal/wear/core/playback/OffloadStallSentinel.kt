@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

/**
 * Detects "playing but inaudible" audio pipelines (UX-023).
 *
 * Compressed offload decouples ExoPlayer's position clock from what the DSP has
 * actually rendered: when the offload path wedges (observed on the SM-R955U with
 * HE-AAC after ~14 s), position keeps advancing on already-pushed bytes while the
 * feed freezes.
 *
 * Signatures (sampled every 2 s while `isPlaying` — a genuinely buffering player
 * reports `isPlaying == false`, so ordinary network stalls never qualify):
 *
 *  - consumption stall: across a full [windowSamples]-sample window the buffered
 *    position never grew, while position advanced by at least [minPositionAdvanceMs]
 *    and still had feed expected before the end;
 *  - render plateau: position frozen for [plateauSamples] consecutive samples while
 *    the player still claims to be playing, far from the end.
 *
 * A window, not adjacent-sample deltas: healthy progressive playback legitimately
 * holds a frozen buffered position for many seconds while draining a segment
 * (the first revision of this sentinel treated that as a stall and rebuilt
 * players every few seconds — a seek storm). Pure state machine; JVM-tested.
 */
class OffloadStallSentinel(
    private val windowSamples: Int = 20,
    private val minPositionAdvanceMs: Long = 15_000,
    private val plateauSamples: Int = 4,
    private val endToleranceMs: Long = 5_000,
) {
    data class Sample(
        val positionMs: Long,
        val bufferedMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val isLoading: Boolean,
    )

    enum class Stall { None, ConsumptionStalled, RenderPlateau }

    private val window = ArrayDeque<Sample>(windowSamples + 1)
    private var previous: Sample? = null
    private var plateauRun = 0

    fun reset() {
        window.clear()
        previous = null
        plateauRun = 0
    }

    fun observe(sample: Sample): Stall {
        if (!sample.isPlaying || sample.isLoading) {
            reset()
            return Stall.None
        }
        val previous = previous
        this.previous = sample

        val hasKnownEnd = sample.durationMs > 0
        val endIsFarEnough = !hasKnownEnd ||
            sample.durationMs - maxOf(sample.positionMs, sample.bufferedMs) > endToleranceMs

        if (previous != null && endIsFarEnough) {
            val positionFrozen =
                kotlin.math.abs(sample.positionMs - previous.positionMs) <= POSITION_EPSILON_MS
            val feedExpected = sample.bufferedMs < sample.durationMs - endToleranceMs
            if (positionFrozen && feedExpected) {
                plateauRun += 1
                if (plateauRun >= plateauSamples) return Stall.RenderPlateau
            } else {
                plateauRun = 0
            }
        } else {
            plateauRun = 0
        }

        window.addLast(sample)
        while (window.size > windowSamples) window.removeFirst()
        if (window.size == windowSamples && endIsFarEnough) {
            val first = window.first()
            val last = window.last()
            val bufferedNeverGrew = window.toList().zipWithNext().all { (a, b) ->
                b.bufferedMs - a.bufferedMs <= POSITION_EPSILON_MS
            }
            val positionAdvanced = last.positionMs - first.positionMs >= minPositionAdvanceMs
            // The window must start with feed ahead of position: at that point the
            // loader still owed bytes, and a healthy loader pays up within the window.
            val feedWasExpected = first.bufferedMs - first.positionMs > POSITION_EPSILON_MS
            if (bufferedNeverGrew && positionAdvanced && feedWasExpected) {
                return Stall.ConsumptionStalled
            }
        }
        return Stall.None
    }

    companion object {
        const val POSITION_EPSILON_MS = 100
    }
}
