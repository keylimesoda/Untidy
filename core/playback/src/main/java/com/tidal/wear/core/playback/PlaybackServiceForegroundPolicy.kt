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

    private val customStartActions = playbackControlActions + setOf(
        PlaybackActions.ACTION_PLAY_FIXTURE,
        PlaybackActions.ACTION_PROBE_DEVICE_AUTH,
        PlaybackActions.ACTION_PLAY_TRACK,
        PlaybackActions.ACTION_PLAY_QUEUE,
    )

    fun shouldStopStartedServiceWhenIdle(action: String?): Boolean = action in playbackControlActions

    fun requiresAppCommandToken(action: String?): Boolean = action in customStartActions
}
