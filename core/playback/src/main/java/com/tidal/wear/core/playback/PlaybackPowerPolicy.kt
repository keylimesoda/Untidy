@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences

/**
 * Battery-first offload policy with proven per-device audibility.
 *
 * Offload (compressed passthrough to the DSP) is the cheapest audio path and is
 * ON by default; quality selection remains the user's TIDAL choice. But offload
 * must never cost audibility: on the SM-R955U the HE-AAC offload pipeline wedged
 * ~14 s in (media.audio_flinger: "0 active" tracks on the OFFLOAD thread, no HAL
 * writes) while Media3 kept reporting PLAYING. [OffloadStallSentinel] proves the
 * failure on-device, and [AudioOffloadSuspension] then pins that device to
 * software decoding (audible; measurably more CPU — FLAC software costs ~18% of
 * one core per beta.6 measurements) instead of paying a silent stall per item.
 */
internal fun playbackAudioOffloadPreferences(suspended: Boolean = false): AudioOffloadPreferences =
    AudioOffloadPreferences.Builder()
        .setAudioOffloadMode(
            if (suspended) {
                AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            } else {
                AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            },
        )
        // Album transitions must not lose gapless playback to save power.
        .setIsGaplessSupportRequired(true)
        .build()
