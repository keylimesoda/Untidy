package com.tidal.wear.core.playback

/**
 * Foreground-service lifecycle policy for explicit service intents.
 *
 * TidalMediaService remains exported because MediaLibraryService/MediaBrowser controls still need
 * validation on real media controllers. These rules only make custom externally-startable actions
 * safe when they are no-ops before playback exists.
 */
internal object PlaybackServiceForegroundPolicy {
    private val playbackControlActions = setOf(
        PlaybackActions.ACTION_PAUSE,
        PlaybackActions.ACTION_RESUME,
        PlaybackActions.ACTION_SKIP_NEXT,
        PlaybackActions.ACTION_SKIP_PREVIOUS,
        PlaybackActions.ACTION_JUMP_TO_QUEUE_INDEX,
    )

    fun shouldStopStartedServiceWhenIdle(action: String?): Boolean = action in playbackControlActions
}
