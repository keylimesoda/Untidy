package com.tidal.wear.ui.settings

import com.tidal.wear.core.model.AudioPreset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPolicyFooterTest {
    @Test
    fun `full fidelity footer describes the selected lossless policy`() {
        val footer = streamingPolicyFooter(AudioPreset.FullFidelity)

        assertTrue(footer.contains("lossless", ignoreCase = true))
        assertTrue(footer.contains("HiRes", ignoreCase = true))
        assertFalse(footer.contains("AAC", ignoreCase = true))
    }

    @Test
    fun `battery saver footer keeps the battery tradeoff explicit`() {
        val footer = streamingPolicyFooter(AudioPreset.BatterySaver)

        assertTrue(footer.contains("AAC", ignoreCase = true))
        assertTrue(footer.contains("battery", ignoreCase = true))
    }
}
