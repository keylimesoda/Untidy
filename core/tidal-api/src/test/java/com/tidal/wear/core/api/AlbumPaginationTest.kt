package com.tidal.wear.core.api

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumPaginationTest {
    @Test
    fun nextPageCursorReadsPlainCursorQueryParameter() {
        val root = jsonWithNext("https://openapi.tidal.com/v2/albums/album-1/relationships/items?page[cursor]=plain-cursor&page[limit]=50")

        assertEquals("plain-cursor", root.nextPageCursor())
    }

    @Test
    fun nextPageCursorReadsEncodedCursorQueryParameter() {
        val root = jsonWithNext("https://openapi.tidal.com/v2/albums/album-1/relationships/items?page%5Bcursor%5D=cursor%3Apage%3D2&page%5Blimit%5D=50")

        assertEquals("cursor:page=2", root.nextPageCursor())
    }

    @Test
    fun nextPageCursorReturnsNullWhenNextLinkMissing() {
        val root = buildJsonObject {
            put("links", buildJsonObject {})
        }

        assertNull(root.nextPageCursor())
    }

    private fun jsonWithNext(next: String) = buildJsonObject {
        put("links", buildJsonObject {
            put("next", next)
        })
    }
}
