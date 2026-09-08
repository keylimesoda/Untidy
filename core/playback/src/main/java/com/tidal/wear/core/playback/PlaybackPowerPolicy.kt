@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences

/** Optimize the selected audio, never change its quality to obtain offload. */
internal fun playbackAudioOffloadPreferences(): AudioOffloadPreferences =
    AudioOffloadPreferences.Builder()
        // Optional: unsupported formats/routes retain ordinary decoded playback.
        .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
        // Album transitions must not lose gapless playback to save power.
        .setIsGaplessSupportRequired(true)
        .build()
