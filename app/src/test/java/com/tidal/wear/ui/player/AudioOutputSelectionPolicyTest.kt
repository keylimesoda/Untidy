package com.tidal.wear.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputSelectionPolicyTest {
    @Test
    fun `keeps an explicitly selected connected output`() {
        assertEquals(42, AudioOutputSelectionPolicy.selectedDeviceId(42, 7, setOf(7, 42)))
    }

    @Test
    fun `falls back to the currently routed output when selection disconnects`() {
        assertEquals(7, AudioOutputSelectionPolicy.selectedDeviceId(42, 7, setOf(7)))
    }

    @Test
    fun `uses current route when there is no explicit preference`() {
        assertEquals(7, AudioOutputSelectionPolicy.selectedDeviceId(null, 7, setOf(7, 42)))
    }

    @Test
    fun `does not claim a selection when route is unknown`() {
        assertEquals(null, AudioOutputSelectionPolicy.selectedDeviceId(null, null, setOf(7)))
    }
}
