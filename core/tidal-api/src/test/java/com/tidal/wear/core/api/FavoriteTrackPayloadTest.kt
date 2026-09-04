package com.tidal.wear.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteTrackPayloadTest {
    @Test
    fun normalizeTrackIdAcceptsPlainAndColonPrefixedIds() {
        assertEquals("123", normalizeTrackId("123"))
        assertEquals("123", normalizeTrackId("tracks:123"))
        assertEquals("123", normalizeTrackId("tidal:track:123"))
    }

    @Test
    fun normalizeTrackIdRejectsSyntheticPlayerIds() {
        assertNull(normalizeTrackId(""))
        assertNull(normalizeTrackId("tidal-current"))
        assertNull(normalizeTrackId("fixture-1"))
    }

    @Test
    fun relationshipBodyUsesJsonApiTrackResourceIdentifierArray() {
        val body = userCollectionTrackRelationshipBody("123")
        val data = body["data"] as JsonArray
        val item = data.first() as JsonObject
        assertEquals(JsonPrimitive("tracks"), item["type"])
        assertEquals(JsonPrimitive("123"), item["id"])
    }

    @Test
    fun favoriteWritesTreatAlreadyPresentAndAlreadyAbsentAsSuccess() {
        assertEquals(true, isFavoriteMutationSuccess(favorite = true, isSuccessful = false, code = 409))
        assertEquals(true, isFavoriteMutationSuccess(favorite = false, isSuccessful = false, code = 404))
        assertEquals(false, isFavoriteMutationSuccess(favorite = true, isSuccessful = false, code = 404))
        assertEquals(false, isFavoriteMutationSuccess(favorite = false, isSuccessful = false, code = 409))
        assertEquals(true, isFavoriteMutationSuccess(favorite = true, isSuccessful = true, code = 200))
    }
}
