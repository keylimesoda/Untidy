@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences

/**
 * Compressed offload is disabled app-wide (UX-021).
 *
 * On the SM-R955U test watch the HE-AAC offload pipeline wedged after ~14 s for both the
 * built-in speaker and A2DP routes: the offload output thread kept reporting "0 active"
 * tracks while Media3 still advanced position and UI, so playback looked healthy but was
 * silent. Software decoding is measurably more expensive (beta.6 CPU notes) but audible;
 * audibility gates power. Re-enable only behind a per-stream audibility probe.
 */
internal fun playbackAudioOffloadPreferences(): AudioOffloadPreferences =
    AudioOffloadPreferences.Builder()
        .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
        .setIsGaplessSupportRequired(true)
        .build()
