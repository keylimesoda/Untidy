package com.tidal.wear.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteAlbumPayloadTest {
    @Test
    fun normalizeAlbumIdAcceptsPlainAndColonPrefixedIds() {
        assertEquals("123", normalizeAlbumId("123"))
        assertEquals("123", normalizeAlbumId("albums:123"))
        assertEquals("123", normalizeAlbumId("tidal:album:123"))
    }

    @Test
    fun normalizeAlbumIdRejectsSyntheticIds() {
        assertNull(normalizeAlbumId(""))
        assertNull(normalizeAlbumId("fixture-album-1"))
    }

    @Test
    fun relationshipBodyUsesJsonApiAlbumResourceIdentifierArray() {
        val body = userCollectionAlbumRelationshipBody("123")
        val data = body["data"] as JsonArray
        val item = data.first() as JsonObject
        assertEquals(JsonPrimitive("albums"), item["type"])
        assertEquals(JsonPrimitive("123"), item["id"])
    }
}
