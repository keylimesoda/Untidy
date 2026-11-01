package com.tidal.wear.core.playback

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPowerPolicyTest {
    // UX-021: forced offload produced silent-but-progressing playback on real hardware.
    @Test fun offloadIsDisabledAndGaplessRemainsRequired() {
        val preferences = playbackAudioOffloadPreferences()
        assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED, preferences.audioOffloadMode)
        assertTrue(preferences.isGaplessSupportRequired)
    }
}
