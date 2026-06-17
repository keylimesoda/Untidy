package com.tidal.wear.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyPaginationTest {
    @Test
    fun legacyObjectsReadsArrayPayload() {
        val items = buildJsonArray {
            add(track("1"))
            add(track("2"))
        }

        assertEquals(listOf("1", "2"), items.legacyIdsForTest())
    }

    @Test
    fun legacyObjectsReadsObjectItemsPayload() {
        val root = buildJsonObject {
            put("items", buildJsonArray {
                add(track("10"))
                add(track("11"))
            })
        }

        assertEquals(listOf("10", "11"), root.legacyIdsForTest())
    }

    private fun JsonArray.legacyIdsForTest(): List<String> = legacyObjects(this).map { it.idForTest() }

    private fun JsonObject.legacyIdsForTest(): List<String> = legacyObjects(this).map { it.idForTest() }

    private fun JsonObject.idForTest(): String = (this["id"] as JsonPrimitive).content

    private fun track(id: String) = buildJsonObject {
        put("id", id)
        put("title", "Track $id")
    }
}
