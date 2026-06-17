package com.tidal.wear.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorUiMessageTest {
    @Test
    fun includesBackendFailureMessageForPlayerUi() {
        assertEquals(
            "Playback failed: manifest playback load failed",
            playbackErrorUiMessage(
                code = "MANIFEST_LOAD_FAILED",
                message = "manifest playback load failed",
            ),
        )
    }

    @Test
    fun fallsBackToBackendCodeWhenMessageIsBlank() {
        assertEquals(
            "Playback failed (NETWORK_TIMEOUT)",
            playbackErrorUiMessage(
                code = "NETWORK_TIMEOUT",
                message = "   ",
            ),
        )
    }

    @Test
    fun capsLongBackendMessagesForRoundDisplay() {
        val longMessage = "x".repeat(160)

        assertEquals(
            "Playback failed: ${"x".repeat(120)}",
            playbackErrorUiMessage(
                code = "MANIFEST_LOAD_FAILED",
                message = longMessage,
            ),
        )
    }
}
