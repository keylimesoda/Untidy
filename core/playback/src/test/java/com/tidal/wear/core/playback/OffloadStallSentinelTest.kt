package com.tidal.wear.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class OffloadStallSentinelTest {
    private val duration = 240_000L

    private fun sample(pos: Long, buffered: Long, playing: Boolean = true, loading: Boolean = false, usesOffload: Boolean = true) =
        OffloadStallSentinel.Sample(
            positionMs = pos,
            bufferedMs = buffered,
            durationMs = duration,
            isPlaying = playing,
            isLoading = loading,
            usesOffload = usesOffload,
        )

    // Production cadence: 2 s samples; defaults = 40 s frozen-buffer window, 15 s crawl.
    private val windowSamples = 20
    private val plateauSamples = 4

    private fun feed(sentinel: OffloadStallSentinel, fromPos: Long, stepMs: Long, count: Int): OffloadStallSentinel.Stall {
        var verdict = OffloadStallSentinel.Stall.None
        for (i in 0 until count) {
            val pos = fromPos + i * stepMs
            verdict = sentinel.observe(sample(pos = pos, buffered = pos + 30_000))
        }
        return verdict
    }

    @Test fun `healthy progressive playback never fires`() {
        val sentinel = OffloadStallSentinel()
        // Buffer refills in segment jumps while position advances: the exact pattern
        // an earlier revision false-positived on.
        var pos = 0L
        var buffered = 30_000L
        repeat(30) { i ->
            pos += 2_000
            if (i % 4 == 3) buffered += 20_000
            assertEquals("sample $i", OffloadStallSentinel.Stall.None, sentinel.observe(sample(pos, buffered)))
        }
    }

    @Test fun `segment drain longer than the window does not fire while loading is healthy afterwards`() {
        val sentinel = OffloadStallSentinel()
        feed(sentinel, fromPos = 5_000, stepMs = 2_000, count = 10)
        var pos = 25_000L
        var buffered = 55_000L
        // Drains a segment (buffered static) but keeps refilling before the window fills.
        repeat(windowSamples - 3) {
            pos += 2_000
            assertEquals(OffloadStallSentinel.Stall.None, sentinel.observe(sample(pos, buffered)))
        }
        buffered += 25_000 // top-up inside the window
        assertEquals(OffloadStallSentinel.Stall.None, sentinel.observe(sample(pos + 2_000, buffered)))
        assertEquals(OffloadStallSentinel.Stall.None, sentinel.observe(sample(pos + 4_000, buffered + 2_000)))
    }

    @Test fun `wedged offload feed is a consumption stall once the window fills`() {
        val sentinel = OffloadStallSentinel()
        // Healthy first (buffered grows), then the feed wedges exactly as on the SM-R955U:
        // buffered frozen at pos+~26 s while position crawls forward.
        feed(sentinel, fromPos = 0, stepMs = 2_000, count = 8)
        var verdict = OffloadStallSentinel.Stall.None
        var firedAtSample = -1
        var pos = 16_000L
        for (i in 0 until 40) {
            pos += 2_000
            verdict = sentinel.observe(sample(pos = pos, buffered = 45_000))
            if (verdict != OffloadStallSentinel.Stall.None) { firedAtSample = i; break }
        }
        assertEquals(OffloadStallSentinel.Stall.ConsumptionStalled, verdict)
        assertEquals("must not fire before the 40 s window", true, firedAtSample >= windowSamples - 8 - 1)
    }

    @Test fun `network buffering never qualifies`() {
        val sentinel = OffloadStallSentinel()
        sentinel.observe(sample(20_000, 40_000))
        repeat(windowSamples) {
            assertEquals(
                OffloadStallSentinel.Stall.None,
                sentinel.observe(sample(20_000, 40_000, loading = true)),
            )
        }
    }

    @Test fun `paused playback never qualifies`() {
        val sentinel = OffloadStallSentinel()
        sentinel.observe(sample(20_000, 40_000))
        repeat(windowSamples) {
            assertEquals(
                OffloadStallSentinel.Stall.None,
                sentinel.observe(sample(20_000, 40_000, playing = false)),
            )
        }
    }

    @Test fun `position plateau while claiming to play fires after the run threshold`() {
        val sentinel = OffloadStallSentinel()
        sentinel.observe(sample(39_000, 39_000))
        for (i in 0 until plateauSamples - 1) {
            assertEquals(OffloadStallSentinel.Stall.None, sentinel.observe(sample(39_000, 39_000)))
        }
        assertEquals(OffloadStallSentinel.Stall.RenderPlateau, sentinel.observe(sample(39_000, 39_000)))
    }

    @Test fun `approaching the real end never fires`() {
        val sentinel = OffloadStallSentinel()
        repeat(windowSamples * 2) {
            assertEquals(OffloadStallSentinel.Stall.None, sentinel.observe(sample(236_000, 236_500)))
        }
    }

    // EXP-014: the SM-R955U 22:31 storm. A bandwidth-limited SOFTWARE stream has
    // exactly the offload-wedge signature (frozen buffered + advancing position
    // while the loader still owes bytes) but is merely draining a shrinking
    // buffer at real time. Consumption must be gated on actual offload state.
    private fun frozenBufferCrawl(usesOffload: Boolean): OffloadStallSentinel.Stall {
        val sentinel = OffloadStallSentinel()
        var verdict = OffloadStallSentinel.Stall.None
        for (i in 0..windowSamples) {
            verdict = sentinel.observe(sample(pos = i * 2_000L, buffered = 60_000, usesOffload = usesOffload))
        }
        return verdict
    }

    @Test fun `offload consumption signature still fires when offload is active`() {
        assertEquals(OffloadStallSentinel.Stall.ConsumptionStalled, frozenBufferCrawl(usesOffload = true))
    }

    @Test fun `identical signature on the software path never consumption-stalls`() {
        assertEquals(OffloadStallSentinel.Stall.None, frozenBufferCrawl(usesOffload = false))
    }

    @Test fun `window with any software sample cannot consumption-stall`() {
        val sentinel = OffloadStallSentinel()
        var verdict = OffloadStallSentinel.Stall.None
        for (i in 0..windowSamples) {
            val offload = i < windowSamples / 2 // half the window predates the software switch
            verdict = sentinel.observe(sample(pos = i * 2_000L, buffered = 60_000, usesOffload = offload))
        }
        assertEquals(OffloadStallSentinel.Stall.None, verdict)
    }

    @Test fun `software render death is still caught by the plateau`() {
        val sentinel = OffloadStallSentinel()
        repeat(plateauSamples) {
            assertEquals(OffloadStallSentinel.Stall.None, sentinel.observe(sample(80_000, 120_000, usesOffload = false)))
        }
        assertEquals(OffloadStallSentinel.Stall.RenderPlateau, sentinel.observe(sample(80_000, 120_000, usesOffload = false)))
    }
}
