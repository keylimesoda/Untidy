package com.tidal.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRouteNavigationPolicyTest {
    @Test
    fun `player artist route replaces stale browsing history and returns to player`() {
        val plan = planExternalRoute(route = "artist/123", returnToPlayer = true)

        assertTrue(plan.clearBackStack)
        assertTrue(plan.returnToPlayer)
    }

    @Test
    fun `player album route replaces stale browsing history and returns to player`() {
        val plan = planExternalRoute(route = "album/456", returnToPlayer = true)

        assertTrue(plan.clearBackStack)
        assertTrue(plan.returnToPlayer)
    }

    @Test
    fun `home route always clears history and has no player return root`() {
        val plan = planExternalRoute(route = "home", returnToPlayer = false)

        assertTrue(plan.clearBackStack)
        assertFalse(plan.returnToPlayer)
    }

    @Test
    fun `ordinary internal route request retains its existing stack behavior`() {
        val plan = planExternalRoute(route = "search", returnToPlayer = false)

        assertFalse(plan.clearBackStack)
        assertFalse(plan.returnToPlayer)
    }

    @Test
    fun `only the exact player handoff entry returns to player`() {
        val rootId = planExternalRoute("artist/123", returnToPlayer = true)
            .returnToPlayerRootEntryId("artist-entry-a")

        assertFalse(isReturnToPlayerRoot(rootId, "artist-entry-b"))
        assertTrue(isReturnToPlayerRoot(rootId, "artist-entry-a"))
    }

    @Test
    fun `non player route has no return entry`() {
        val rootId = planExternalRoute("search", returnToPlayer = false)
            .returnToPlayerRootEntryId("search-entry")

        assertNull(rootId)
    }

    @Test
    fun `signed in startup does not overwrite a pending external route`() {
        assertFalse(shouldNavigateSignedInHome(hasPendingRouteRequest = true, currentRoute = null))
        assertFalse(shouldNavigateSignedInHome(hasPendingRouteRequest = true, currentRoute = "onboarding"))
        assertTrue(shouldNavigateSignedInHome(hasPendingRouteRequest = false, currentRoute = null))
    }
}
