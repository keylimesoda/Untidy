package com.tidal.wear.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveredAudioFormatTest {
    @Test
    fun mapsReportedAudioFormatFields() {
        val format = Format.Builder()
            .setCodecs("mp4a.40.2")
            .setSampleMimeType("audio/mp4")
            .setContainerMimeType("audio/mp4")
            .setAverageBitrate(256_000)
            .setSampleRate(44_100)
            .setChannelCount(2)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        assertEquals(
            DeliveredAudioFormat(
                codec = "mp4a.40.2",
                container = "audio/mp4",
                bitrate = 256_000,
                sampleRateHz = 44_100,
                channelCount = 2,
                bitDepth = 16,
            ),
            format.toDeliveredAudioFormat(),
        )
    }

    @Test
    fun leavesUnavailableValuesUnknownInsteadOfGuessing() {
        val format = Format.Builder()
            .setSampleMimeType("audio/aac")
            .setAverageBitrate(Format.NO_VALUE)
            .setSampleRate(Format.NO_VALUE)
            .setChannelCount(Format.NO_VALUE)
            .setPcmEncoding(C.ENCODING_INVALID)
            .build()

        val mapped = format.toDeliveredAudioFormat()

        assertEquals("audio/aac", mapped.codec)
        assertNull(mapped.container)
        assertNull(mapped.bitrate)
        assertNull(mapped.sampleRateHz)
        assertNull(mapped.channelCount)
        assertNull(mapped.bitDepth)
    }

}
