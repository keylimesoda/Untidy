package com.tidal.wear.core.playback

import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * App-private guard for explicit service command intents.
 *
 * TidalMediaService is exported for MediaLibraryService/controller discovery while Wear/system
 * media integration is validated. Exporting the service also makes explicit custom actions
 * externally startable, so app-authored commands carry a per-install token stored in app-private
 * preferences. Media3 controllers still use the session connection policy; this guard covers
 * startForegroundService()/PendingIntent custom actions only.
 */
object PlaybackCommandTokenProvider {
    private const val PREFS = "tidal_playback_command_guard"
    private const val KEY_TOKEN = "command_token"

    @Synchronized
    fun token(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_TOKEN, generated).apply()
        return generated
    }

    fun isValid(context: Context, intent: Intent?): Boolean {
        val provided = intent?.getStringExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return provided == token(context)
    }
}
