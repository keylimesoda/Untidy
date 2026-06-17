package com.tidal.wear.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tidal.wear.core.playback.PlaybackActions
import com.tidal.wear.core.playback.PlaybackCommandTokenProvider
import com.tidal.wear.core.playback.TidalMediaService

/** Debug-only trampoline for validating #26 playback routing from an app-authored command. */
class OfflinePlaybackValidationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trackId = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty().ifBlank { "5120026" }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Why Go" }
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty().ifBlank { "Pearl Jam" }
        val album = intent.getStringExtra(EXTRA_ALBUM).orEmpty().ifBlank { "Ten" }
        getSharedPreferences("offline-downloads", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("downloaded:$trackId", true)
            .putString("title:$trackId", title)
            .putString("artist:$trackId", artist)
            .putLong("downloadedAt:$trackId", System.currentTimeMillis())
            .apply()
        val playbackIntent = Intent(this, TidalMediaService::class.java)
            .setAction(PlaybackActions.ACTION_PLAY_TRACK)
            .putExtra(PlaybackActions.EXTRA_TRACK_ID, trackId)
            .putExtra(PlaybackActions.EXTRA_TITLE, title)
            .putExtra(PlaybackActions.EXTRA_ARTIST, artist)
            .putExtra(PlaybackActions.EXTRA_ALBUM, album)
            .putExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN, PlaybackCommandTokenProvider.token(this))
        startForegroundService(playbackIntent)
        finish()
    }

    companion object {
        const val EXTRA_TRACK_ID = "trackId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
    }
}
