package com.tidal.wear.core.playback

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPowerPolicyTest {
    @Test fun offloadIsOptionalAndPreservesGaplessAudio() {
        val preferences = playbackAudioOffloadPreferences()
        assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED, preferences.audioOffloadMode)
        assertTrue(preferences.isGaplessSupportRequired)
    }
}
