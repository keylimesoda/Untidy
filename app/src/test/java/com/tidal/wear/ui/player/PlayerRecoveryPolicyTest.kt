package com.tidal.wear.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRecoveryPolicyTest {
    @Test
    fun `empty player uses a recovery message instead of a loading spinner`() {
        assertEquals("Nothing is playing", PlayerRecoveryPolicy.emptyMessage(null))
        assertEquals("Playback unavailable", PlayerRecoveryPolicy.emptyMessage("Playback unavailable"))
        assertEquals(
            "Downloaded copy needs repair. Connect and redownload it.",
            PlayerRecoveryPolicy.emptyMessage("Downloaded track needs repair: local cache is incomplete or corrupt"),
        )
    }

    @Test
    fun `same track cannot start a second playlist write while first is pending`() {
        assertFalse(PlayerRecoveryPolicy.canStartPlaylistWrite("42", "42"))
        assertFalse(PlayerRecoveryPolicy.canStartPlaylistWrite("42", "43"))
        assertTrue(PlayerRecoveryPolicy.canStartPlaylistWrite(null, "42"))
    }
}
