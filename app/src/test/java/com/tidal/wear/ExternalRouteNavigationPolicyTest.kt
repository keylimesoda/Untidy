package com.tidal.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRouteNavigationPolicyTest {
    @Test
    fun `player artist route replaces stale browsing history and returns to player`() {
        val plan = planExternalRoute(route = "artist/123", returnToPlayer = true)

        assertTrue(plan.clearBackStack)
        assertEquals("artist/{artistId}", plan.returnRootPattern)
    }

    @Test
    fun `player album route replaces stale browsing history and returns to player`() {
        val plan = planExternalRoute(route = "album/456", returnToPlayer = true)

        assertTrue(plan.clearBackStack)
        assertEquals("album/{albumId}", plan.returnRootPattern)
    }

    @Test
    fun `home route always clears history and has no player return root`() {
        val plan = planExternalRoute(route = "home", returnToPlayer = false)

        assertTrue(plan.clearBackStack)
        assertNull(plan.returnRootPattern)
    }

    @Test
    fun `ordinary internal route request retains its existing stack behavior`() {
        val plan = planExternalRoute(route = "search", returnToPlayer = false)

        assertFalse(plan.clearBackStack)
        assertNull(plan.returnRootPattern)
    }
}
