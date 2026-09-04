package com.tidal.wear.core.playback

import com.tidal.wear.core.model.AudioPreset
import com.tidal.sdk.player.common.model.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPresetQualityMappingTest {
    @Test
    fun fullFidelityUsesLosslessBeforeAacFallback() {
        assertEquals(
            listOf(
                listOf("FLAC_HIRES"),
                listOf("FLAC"),
                listOf("HEAACV1", "AACLC"),
            ),
            manifestFormatAttempts(AudioPreset.FullFidelity),
        )
    }

    @Test
    fun existingPresetsKeepTheirAacRequests() {
        assertEquals(listOf(listOf("HEAACV1")), manifestFormatAttempts(AudioPreset.BatterySaver))
        assertEquals(listOf(listOf("HEAACV1", "AACLC")), manifestFormatAttempts(AudioPreset.Balanced))
        assertEquals(listOf(listOf("HEAACV1", "AACLC")), manifestFormatAttempts(AudioPreset.High))
    }

    @Test
    fun fullFidelityUsesHighestVerifiedLegacyQualityName() {
        assertEquals("HI_RES_LOSSLESS", legacyAudioQualityForPreset(AudioPreset.FullFidelity))
    }

    @Test
    fun sdkBackendUsesHighestVerifiedAudioQualityEnum() {
        assertEquals(AudioQuality.HI_RES_LOSSLESS, sdkAudioQualityForPreset(AudioPreset.FullFidelity))
    }
}
