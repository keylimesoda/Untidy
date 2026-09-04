package com.tidal.wear.core.api

import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEfficiencyPolicyTest {
    @Test
    fun searchDoesNotFallbackWhenV2ReturnsOnlyEntityResults() {
        val result = TidalSearchResult(albums = listOf(TidalAlbum(id = "album-1", title = "Album")))

        assertFalse(shouldFallbackToLegacySearch(result))
    }

    @Test
    fun searchFallsBackOnlyWhenV2ResultIsCompletelyEmpty() {
        assertTrue(shouldFallbackToLegacySearch(TidalSearchResult()))
    }

    @Test
    fun favoriteCacheLoadsOnceWithinTtlAndWritesThroughMutations() = runBlocking {
        var now = 1_000L
        var loads = 0
        val cache = FavoriteTrackCache(ttlMillis = 30_000L, nowMillis = { now })
        val loader = suspend {
            loads += 1
            setOf("one", "two")
        }

        assertEquals(setOf("one", "two"), cache.getOrLoad(loader))
        assertEquals(setOf("one", "two"), cache.getOrLoad(loader))
        assertEquals(1, loads)

        cache.update("one", favorite = false)
        assertEquals(setOf("two"), cache.getOrLoad(loader))
        assertEquals(1, loads)

        now += 30_000L
        assertEquals(setOf("one", "two"), cache.getOrLoad(loader))
        assertEquals(2, loads)
    }

    @Test
    fun favoriteCacheSerializesConcurrentLoads() = runBlocking {
        var loads = 0
        val cache = FavoriteTrackCache(ttlMillis = 30_000L)
        val loader = suspend {
            loads += 1
            delay(10)
            setOf("one")
        }

        val first = async { cache.getOrLoad(loader) }
        val second = async { cache.getOrLoad(loader) }

        assertEquals(setOf("one"), first.await())
        assertEquals(setOf("one"), second.await())
        assertEquals(1, loads)
    }
}
