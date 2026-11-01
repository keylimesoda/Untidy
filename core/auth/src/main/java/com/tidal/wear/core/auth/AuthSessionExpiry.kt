package com.tidal.wear.core.auth

// A refresh rejected with these codes means the refresh token itself is dead
// server-side (revoked or expired after long disuse); no retry can recover it,
// so the stored session must be dropped and the user asked to sign in again.
// 429/5xx are transient and NEVER clear the session.
internal fun isPermanentRefreshFailure(code: Int): Boolean =
    code == 400 || code == 401 || code == 403
