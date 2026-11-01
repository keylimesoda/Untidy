package com.tidal.wear.core.auth

import kotlin.math.max
import kotlin.math.min

// Progressive OAuth backoff: each slow_down pushes the next poll at least
// five seconds past the current interval (RFC 8628 §3.5), never below the
// server-provided value, capped to stay inside the short code lifetime.
internal fun nextSlowDownIntervalSeconds(currentSeconds: Long, requestedSeconds: Long): Long =
    min(max(requestedSeconds, currentSeconds + 5L), 60L)
