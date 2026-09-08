package com.tidal.wear.ui.player

/** Pure policy so failure and retry UI cannot regress into an unbounded loading state. */
internal object PlayerRecoveryPolicy {
    fun needsDownloadRepair(error: String?): Boolean =
        error.orEmpty().contains("Downloaded track needs repair", ignoreCase = true)
    fun emptyMessage(error: String?): String = when {
        error.isNullOrBlank() -> "Nothing is playing"
        error.contains("Downloaded track needs repair", ignoreCase = true) ->
            "Downloaded copy needs repair. Connect and redownload it."
        else -> error
    }

    fun canStartPlaylistWrite(pendingTrackId: String?, trackId: String): Boolean =
        pendingTrackId == null
}
