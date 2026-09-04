package com.tidal.wear.core.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Retrofit

private val requestShapeJson = Json { ignoreUnknownKeys = true }

class TidalApiServiceRequestShapeTest {
    @Test
    fun searchUsesOfficialV2FilterQueryAndRepeatedIncludes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":[]}"))
            val service = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(requestShapeJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TidalApiService::class.java)

            service.search(query = "safe search", countryCode = "US")

            val request = server.takeRequest()
            val url = requireNotNull(request.requestUrl)
            assertEquals("/searchResults", url.encodedPath)
            assertEquals(listOf("safe search"), url.queryParameterValues("filter[query]"))
            assertFalse(url.queryParameterNames.contains("query"))
            assertEquals(
                listOf("tracks", "albums", "artists", "playlists"),
                url.queryParameterValues("include"),
            )
            assertEquals("application/vnd.api+json", request.getHeader("accept"))
        }
    }

    @Test
    fun favoriteRelationshipLookupSupportsJsonApiCursorParameters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":[]}"))
            val service = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(requestShapeJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TidalApiService::class.java)

            service.userCollectionTrackItems(
                id = "me",
                countryCode = "US",
                pageLimit = 50,
                pageCursor = "cursor:page=2",
            )

            val url = requireNotNull(server.takeRequest().requestUrl)
            assertEquals("/userCollectionTracks/me/relationships/items", url.encodedPath)
            assertEquals(listOf("50"), url.queryParameterValues("page[limit]"))
            assertEquals(listOf("cursor:page=2"), url.queryParameterValues("page[cursor]"))
        }
    }

    @Test
    fun favoriteAlbumRelationshipUsesAlbumCollectionEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":[]}"))
            val service = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(requestShapeJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TidalApiService::class.java)

            service.userCollectionAlbumItems(
                id = "me",
                countryCode = "US",
                pageLimit = 50,
                pageCursor = "album-page-2",
            )

            val url = requireNotNull(server.takeRequest().requestUrl)
            assertEquals("/userCollectionAlbums/me/relationships/items", url.encodedPath)
            assertEquals(listOf("50"), url.queryParameterValues("page[limit]"))
            assertEquals(listOf("album-page-2"), url.queryParameterValues("page[cursor]"))
        }
    }
}
