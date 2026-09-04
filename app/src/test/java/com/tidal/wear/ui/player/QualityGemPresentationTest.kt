package com.tidal.wear.ui.player

import com.tidal.wear.core.playback.DeliveredAudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityGemPresentationTest {
    @Test
    fun `hi res lossless uses max gem and compact bit depth sample rate`() {
        val result = DeliveredAudioFormat(
            codec = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 48_000,
        ).toQualityGemPresentation()

        assertEquals("24-bit 48.0kHz", result?.label)
        assertEquals(DeliveredQualityTier.Max, result?.tier)
    }

    @Test
    fun `lossless cd quality uses high gem`() {
        val result = DeliveredAudioFormat(
            codec = "flac",
            bitDepth = 16,
            sampleRateHz = 44_100,
        ).toQualityGemPresentation()

        assertEquals("16-bit 44.1kHz", result?.label)
        assertEquals(DeliveredQualityTier.High, result?.tier)
    }

    @Test
    fun `compressed format uses bitrate without exposing codec`() {
        val result = DeliveredAudioFormat(
            codec = "mp4a.40.2",
            bitrate = 320_000,
            sampleRateHz = 44_100,
        ).toQualityGemPresentation()

        assertEquals("320kbps 44.1kHz", result?.label)
        assertEquals(DeliveredQualityTier.Low, result?.tier)
    }

    @Test
    fun `unknown values do not create a misleading gem`() {
        assertNull(DeliveredAudioFormat(codec = "audio/unknown").toQualityGemPresentation())
    }
}
