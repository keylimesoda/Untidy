package com.tidal.wear.ui.player

import android.content.Context

/** One-time coaching for the deliberately non-tappable More grip. */
internal object MoreActionsHintPolicy {
    fun shouldShow(hasTrack: Boolean, hasBeenShown: Boolean): Boolean = hasTrack && !hasBeenShown
}

internal object MoreActionsHintPreference {
    private const val PREFERENCES_NAME = "untidy_player"
    private const val KEY_HAS_SHOWN = "has_shown_more_actions_hint"

    fun hasBeenShown(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_HAS_SHOWN, false)

    fun markShown(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_SHOWN, true)
            .apply()
    }
}
