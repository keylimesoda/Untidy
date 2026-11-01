package com.tidal.wear.core.playback

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPowerPolicyTest {
    @Test fun offloadIsBatteryDefaultUntilProvenBroken() {
        val preferences = playbackAudioOffloadPreferences(suspended = false)
        assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED, preferences.audioOffloadMode)
        assertTrue(preferences.isGaplessSupportRequired)
    }

    // UX-021: a device whose offload pipeline stalls (sentinel-proven) stays on software decode.
    @Test fun suspendedDeviceDisablesOffload() {
        val preferences = playbackAudioOffloadPreferences(suspended = true)
        assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED, preferences.audioOffloadMode)
        assertTrue(preferences.isGaplessSupportRequired)
    }
}
