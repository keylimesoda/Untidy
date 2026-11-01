package com.tidal.wear.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Session-expiry decisions must never confuse a dead refresh token (permanent
 * 4xx -> re-auth) with transient failures (must keep the session).
 */
class AuthSessionExpiryTest {

    @Test
    fun deadRefreshTokenCodesArePermanentFailures() {
        assertEquals(true, isPermanentRefreshFailure(400)) // invalid_grant
        assertEquals(true, isPermanentRefreshFailure(401))
        assertEquals(true, isPermanentRefreshFailure(403))
    }

    @Test
    fun transientAndUnknownCodesKeepTheSession() {
        assertEquals(false, isPermanentRefreshFailure(429))
        assertEquals(false, isPermanentRefreshFailure(500))
        assertEquals(false, isPermanentRefreshFailure(503))
        assertEquals(false, isPermanentRefreshFailure(-1)) // network failure, no HTTP code
    }
}
