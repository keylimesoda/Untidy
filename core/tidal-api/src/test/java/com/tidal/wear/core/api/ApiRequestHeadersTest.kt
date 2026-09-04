package com.tidal.wear.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiRequestHeadersTest {
    @Test
    fun defaultsMatchTheResponseFormatEachApiExpects() {
        assertEquals("application/vnd.api+json", defaultApiAccept("openapi.tidal.com"))
        assertEquals("application/json", defaultApiAccept("api.tidal.com"))
        assertEquals("application/json", defaultApiAccept("listen.tidal.com"))
    }

    @Test
    fun openApiHostMatchingIsCaseInsensitive() {
        assertEquals("application/vnd.api+json", defaultApiAccept("OPENAPI.TIDAL.COM"))
    }

    @Test
    fun cancellationDoesNotInvokeSearchFallback() = runBlocking {
        var fallbackCalled = false
        try {
            requestWithFallback(
                request = { throw CancellationException("test cancellation") },
                fallback = {
                    fallbackCalled = true
                    error("fallback must not run")
                },
            )
        } catch (_: CancellationException) {
            // Expected: cancellation belongs to the caller's coroutine.
        }
        assertFalse(fallbackCalled)
    }

    @Test
    fun anyHttpFailureUsesSearchFallback() = runBlocking {
        listOf(400, 406, 429, 500, 503).forEach { status ->
            var fallbackCalled = false
            val result = requestWithFallback(
                request = { throw HttpException(errorResponse(status)) },
                fallback = { failure ->
                    fallbackCalled = true
                    assertEquals(status, (failure as HttpException).code())
                    "fallback"
                },
            )

            assertEquals("fallback", result)
            assertTrue(fallbackCalled)
        }
    }

    @Test
    fun fallbackFailureStillPropagates() = runBlocking {
        var propagated = false
        try {
            requestWithFallback<String>(
                request = { throw HttpException(errorResponse(503)) },
                fallback = { error("fallback failed") },
            )
        } catch (failure: IllegalStateException) {
            propagated = failure.message == "fallback failed"
        }
        assertTrue(propagated)
    }

    private fun errorResponse(status: Int): Response<Unit> = Response.error(
        status,
        "{}".toResponseBody("application/json".toMediaType()),
    )
}
