package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackRequestState(
    val track: TidalTrack? = null,
    val queue: List<TidalTrack> = emptyList(),
    val index: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Same-process service/UI handoff for failures before Media3 has a media item to report. */
object PlaybackRequestStatus {
    private val mutable = MutableStateFlow(PlaybackRequestState())
    val state = mutable.asStateFlow()

    internal fun begin(track: TidalTrack, queue: List<TidalTrack>, index: Int) {
        mutable.value = PlaybackRequestState(track, queue, index, loading = true)
    }
    internal fun fail(message: String) {
        mutable.value = mutable.value.copy(loading = false, error = message)
    }
    internal fun ready() {
        mutable.value = mutable.value.copy(loading = false, error = null)
    }
}

internal fun playbackFailureMessage(error: Throwable): String =
    if (error.message.orEmpty().contains("Downloaded track needs repair")) {
        "Downloaded track needs repair. Connect and repair the download."
    } else {
        "Couldn't start playback. Check your connection and retry."
    }
