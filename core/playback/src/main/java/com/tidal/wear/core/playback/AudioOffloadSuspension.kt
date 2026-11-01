package com.tidal.wear.core.playback

import android.content.Context

/**
 * Per-device record of audio offload failures (UX-021/UX-023).
 *
 * Offload is the right battery choice by default, but some devices' offload
 * pipelines die mid-stream while the player keeps reporting healthy playback.
 * Once the sentinel proves that on THIS device, offload stays suspended (prefs are
 * per-device) so playback is software-decoded from the start instead of stalling
 * again on every item.
 */
object AudioOffloadSuspension {
    private const val PREFERENCES_NAME = "untidy_playback"
    private const val KEY_SUSPENDED = "audio_offload_suspended"

    fun isSuspended(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_SUSPENDED, false)

    fun suspend(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUSPENDED, true)
            .apply()
    }
}
