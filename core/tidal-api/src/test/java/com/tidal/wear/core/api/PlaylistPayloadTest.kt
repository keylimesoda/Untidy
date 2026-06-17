package com.tidal.wear.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
