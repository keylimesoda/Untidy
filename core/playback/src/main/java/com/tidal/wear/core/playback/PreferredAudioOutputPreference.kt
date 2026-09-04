package com.tidal.wear.core.playback

import android.content.Context

/** Persists Untidy's app-scoped output preference; it never changes the system's Bluetooth route. */
object PreferredAudioOutputPreference {
    private const val PREFERENCES_NAME = "untidy_playback"
    private const val KEY_DEVICE_ID = "preferred_audio_output_device_id"
    private const val AUTOMATIC = -1

    fun get(context: Context): Int? = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_DEVICE_ID, AUTOMATIC)
        .takeIf { it >= 0 }

    fun set(context: Context, deviceId: Int?) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DEVICE_ID, deviceId ?: AUTOMATIC)
            .apply()
    }
}
