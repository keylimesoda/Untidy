package com.tidal.wear.core.playback

import com.tidal.wear.core.model.AudioPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTierResolverTest {
    private val resolver = AudioTierResolver()

    @Test
    fun batterySaverPrefersHardwareAac() {
        val tier = resolver.resolve(
            preset = AudioPreset.BatterySaver,
            decoders = listOf(
                DecoderCapability("vendor.aac", "audio/mp4a-latm", hardwareAccelerated = true, softwareOnly = false),
                DecoderCapability("google.flac", "audio/flac", hardwareAccelerated = false, softwareOnly = true),
            ),
        )

        assertEquals("audio/mp4a-latm", tier.mimeType)
        assertEquals(96, tier.targetBitrateKbps)
    }

    @Test
    fun cellularForcesBatterySaver() {
        val tier = resolver.resolve(
            preset = AudioPreset.High,
            cellular = true,
            decoders = listOf(DecoderCapability("vendor.aac", "audio/mp4a-latm", true, false)),
        )

        assertEquals(AudioPreset.BatterySaver, tier.preset)
        assertEquals(96, tier.targetBitrateKbps)
    }
}
