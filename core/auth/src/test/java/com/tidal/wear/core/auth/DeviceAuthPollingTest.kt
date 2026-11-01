package com.tidal.wear.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAuthPollingTest {
    @Test
    fun `single slow down honors server interval`() {
        assertEquals(10L, nextSlowDownIntervalSeconds(currentSeconds = 5, requestedSeconds = 10))
    }

    @Test
    fun `repeated slow downs back off progressively`() {
        var interval = 5L
        val observed = mutableListOf(interval)
        repeat(4) {
            interval = nextSlowDownIntervalSeconds(interval, 10L)
            observed += interval
        }
        assertEquals(listOf(5L, 10L, 15L, 20L, 25L), observed)
    }

    @Test
    fun `slow down never shortens the current interval`() {
        assertEquals(25L, nextSlowDownIntervalSeconds(currentSeconds = 20, requestedSeconds = 10))
    }

    @Test
    fun `backoff is capped to stay inside short code lifetime`() {
        assertEquals(60L, nextSlowDownIntervalSeconds(currentSeconds = 58, requestedSeconds = 10))
    }
}
