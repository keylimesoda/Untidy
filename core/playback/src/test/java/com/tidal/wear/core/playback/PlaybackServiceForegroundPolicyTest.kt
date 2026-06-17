package com.tidal.wear.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceForegroundPolicyTest {
    @Test
    fun idlePlaybackControlsStopStartedServiceInsteadOfWaitingForForeground() {
        assertTrue(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_PAUSE))
        assertTrue(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_RESUME))
        assertTrue(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_SKIP_NEXT))
        assertTrue(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_SKIP_PREVIOUS))
        assertTrue(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_JUMP_TO_QUEUE_INDEX))
    }

    @Test
    fun playbackStartingActionsAreAllowedToPublishForegroundNotification() {
        assertFalse(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_PLAY_FIXTURE))
        assertFalse(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_PLAY_TRACK))
        assertFalse(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_PLAY_QUEUE))
        assertFalse(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(PlaybackActions.ACTION_PROBE_DEVICE_AUTH))
        assertFalse(PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(null))
    }
}
