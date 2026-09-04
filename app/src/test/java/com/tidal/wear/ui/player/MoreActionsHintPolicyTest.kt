package com.tidal.wear.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreActionsHintPolicyTest {
    @Test
    fun `shows only for a playing screen that has not learned the gesture`() {
        assertTrue(MoreActionsHintPolicy.shouldShow(hasTrack = true, hasBeenShown = false))
    }

    @Test
    fun `does not recur after the hint has been shown or before a track loads`() {
        assertFalse(MoreActionsHintPolicy.shouldShow(hasTrack = true, hasBeenShown = true))
        assertFalse(MoreActionsHintPolicy.shouldShow(hasTrack = false, hasBeenShown = false))
    }
}
