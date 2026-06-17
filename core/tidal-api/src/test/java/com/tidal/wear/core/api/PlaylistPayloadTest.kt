package com.tidal.wear.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PlaylistPayloadTest {
    @Test
    fun playlistRelationshipBodyUsesJsonApiTrackResourceIdentifierArray() {
        val body = playlistTrackRelationshipBody("456")
        val data = body["data"] as JsonArray
        val item = data.first() as JsonObject
        assertEquals(JsonPrimitive("tracks"), item["type"])
        assertEquals(JsonPrimitive("456"), item["id"])
    }

    @Test
    fun favoriteAndPlaylistTrackBodiesShareWriteShape() {
        assertEquals(
            userCollectionTrackRelationshipBody("789"),
            playlistTrackRelationshipBody("789"),
        )
    }

    @Test
    fun normalizeTrackIdAcceptsPlainAndUriIds() {
        assertEquals("123", normalizeTrackId("123"))
        assertEquals("456", normalizeTrackId("tidal:track:456"))
        assertEquals("789", normalizeTrackId("  tidal:track:789  "))
    }

    @Test
    fun normalizeTrackIdRejectsSyntheticOrBlankIds() {
        assertNull(normalizeTrackId(""))
        assertNull(normalizeTrackId("   "))
        assertNull(normalizeTrackId("tidal-current"))
        assertNull(normalizeTrackId("fixture-run-01"))
    }

    @Test
    fun addTrackToPlaylistOutcomeMapsSuccessfulWriteToAdded() {
        assertEquals(
            com.tidal.wear.core.model.AddTrackToPlaylistOutcome.Added,
            addTrackToPlaylistOutcome(Response.success(Unit)),
        )
    }

    @Test
    fun addTrackToPlaylistOutcomeMapsConflictToAlreadyPresent() {
        assertEquals(
            com.tidal.wear.core.model.AddTrackToPlaylistOutcome.AlreadyPresent,
            addTrackToPlaylistOutcome(errorResponse(409)),
        )
    }

    @Test(expected = HttpException::class)
    fun addTrackToPlaylistOutcomeThrowsForPermissionFailure() {
        addTrackToPlaylistOutcome(errorResponse(403))
    }

    @Test(expected = HttpException::class)
    fun addTrackToPlaylistOutcomeThrowsForMissingPlaylist() {
        addTrackToPlaylistOutcome(errorResponse(404))
    }

    private fun errorResponse(code: Int): Response<Unit> = Response.error(
        code,
        "{}".toResponseBody("application/json".toMediaType()),
    )
}
