package com.tidal.wear.core.api

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalDiscoverSection
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalSection
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.Locale
import java.util.concurrent.TimeUnit

interface TidalApiService {
    @GET("searchResults")
    suspend fun search(
        @Query("query") query: String,
        @Query("countryCode") countryCode: String,
        @Query("include") include: String = "tracks,albums,artists,playlists",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userDiscoveryMixes/me")
    suspend fun userDiscoveryMixes(
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userDiscoveryMixes/{id}/relationships/items")
    suspend fun userDiscoveryMixItems(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("page[cursor]") pageCursor: String? = null,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userDailyMixes/me")
    suspend fun userDailyMixes(
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userDailyMixes/{id}/relationships/items")
    suspend fun userDailyMixItems(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("page[cursor]") pageCursor: String? = null,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userNewReleaseMixes/me")
    suspend fun userNewReleaseMixes(
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userNewReleaseMixes/{id}/relationships/items")
    suspend fun userNewReleaseMixItems(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("page[cursor]") pageCursor: String? = null,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userRecommendations/{id}")
    suspend fun userRecommendations(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("locale") locale: String,
        @Query("include") include: String = "discoveryMixes,myMixes,newArrivalMixes",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("users/me")
    suspend fun currentUser(
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("userCollections/me")
    suspend fun favorites(
        @Query("countryCode") countryCode: String,
        @Query("include") include: String = "albums,playlists,artists,tracks",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("playlists/{id}")
    suspend fun playlist(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("playlists/{id}/relationships/items")
    suspend fun playlistItems(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("albums/{id}")
    suspend fun album(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("albums/{id}/relationships/items")
    suspend fun albumItems(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("include") include: String = "items",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("tracks/{id}")
    suspend fun track(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("artists/{id}")
    suspend fun artist(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("artists/{id}/relationships/tracks")
    suspend fun artistTracks(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("include") include: String = "tracks,tracks.albums",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement

    @GET("artists/{id}/relationships/albums")
    suspend fun artistAlbums(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("include") include: String = "albums",
        @Header("accept") accept: String = JSON_API_ACCEPT,
    ): JsonElement
}

interface TidalLegacyApiService {
    @GET("search")
    suspend fun search(
        @Query("query") query: String,
        @Query("countryCode") countryCode: String,
        @Query("types") types: String = "TRACKS,ALBUMS,ARTISTS,PLAYLISTS",
        @Query("token") token: String,
        @Query("limit") limit: Int = 25,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("albums/{id}")
    suspend fun album(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("albums/{id}/tracks")
    suspend fun albumTracks(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 50,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("artists/{id}")
    suspend fun artist(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("artists/{id}/toptracks")
    suspend fun artistTopTracks(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 25,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("artists/{id}/albums")
    suspend fun artistAlbums(
        @Path("id") id: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 25,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("users/{userId}/favorites/tracks")
    suspend fun favoriteTracks(
        @Path("userId") userId: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 50,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("users/{userId}/favorites/albums")
    suspend fun favoriteAlbums(
        @Path("userId") userId: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 50,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("users/{userId}/favorites/artists")
    suspend fun favoriteArtists(
        @Path("userId") userId: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 50,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET("users/{userId}/favorites/playlists")
    suspend fun favoritePlaylists(
        @Path("userId") userId: String,
        @Query("countryCode") countryCode: String,
        @Query("token") token: String,
        @Query("limit") limit: Int = 50,
        @Header("accept") accept: String = "application/json",
    ): JsonElement
}

interface TidalDesktopApiService {
    @GET
    suspend fun search(
        @Url url: String,
        @Query("query") query: String,
        @Query("countryCode") countryCode: String,
        @Query("types") types: String = "TRACKS,ALBUMS,ARTISTS,PLAYLISTS",
        @Query("limit") limit: Int = 25,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET
    suspend fun album(
        @Url url: String,
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET
    suspend fun albumTracks(
        @Url url: String,
        @Query("countryCode") countryCode: String,
        @Query("limit") limit: Int = 50,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET
    suspend fun artist(
        @Url url: String,
        @Query("countryCode") countryCode: String,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET
    suspend fun artistTopTracks(
        @Url url: String,
        @Query("countryCode") countryCode: String,
        @Query("limit") limit: Int = 25,
        @Header("accept") accept: String = "application/json",
    ): JsonElement

    @GET
    suspend fun artistAlbums(
        @Url url: String,
        @Query("countryCode") countryCode: String,
        @Query("limit") limit: Int = 25,
        @Header("accept") accept: String = "application/json",
    ): JsonElement
}

class TidalApiClient(
    private val authRepository: TidalAuthRepository,
    private val countryCode: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US",
) {
    private val locale: String = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() } ?: "en-US"
    private val json = Json { ignoreUnknownKeys = true }
    private val service: TidalApiService
    private val legacyService: TidalLegacyApiService
    private val desktopService: TidalDesktopApiService

    init {
        val client = authenticatedClient(JSON_API_ACCEPT)
        service = Retrofit.Builder()
            .baseUrl("https://openapi.tidal.com/v2/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TidalApiService::class.java)
        legacyService = Retrofit.Builder()
            .baseUrl("https://api.tidal.com/v1/")
            .client(authenticatedClient("application/json"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TidalLegacyApiService::class.java)
        desktopService = Retrofit.Builder()
            .baseUrl("https://listen.tidal.com/")
            .client(authenticatedClient("application/json"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TidalDesktopApiService::class.java)
    }

    private fun authenticatedClient(accept: String): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = runBlocking { authRepository.getAccessToken() }
                val clientId = runBlocking { authRepository.getClientIdForApi() }
                val request = chain.request().newBuilder()
                    .header("accept", accept)
                    .header("X-Tidal-Token", clientId)
                    .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                    .build()
                chain.proceed(request)
            }
            .build()

    suspend fun search(query: String): TidalSearchResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return TidalSearchResult()
        return runCatching { parseSearch(service.search(trimmed, countryCode)) }
            .fold(
                onSuccess = {
                    Log.d(API_LOG_TAG, "search v2 ok queryChars=${trimmed.length} tracks=${it.tracks.size}")
                    if (it.tracks.isNotEmpty()) it else legacySearch(trimmed, "v2-empty")
                },
                onFailure = {
                    if (it is HttpException && it.code() !in setOf(401, 403, 404)) throw it
                    legacySearch(trimmed, "v2-${it.safeReason()}")
                },
            )
    }
    suspend fun currentUser(): JsonElement = service.currentUser(countryCode)
    suspend fun favorites(): TidalSearchResult = runCatching { parseSearch(service.favorites(countryCode)) }
        .fold(
            onSuccess = {
                Log.d(API_LOG_TAG, "library v2 ok tracks=${it.tracks.size} albums=${it.albums.size} artists=${it.artists.size} playlists=${it.playlists.size}")
                if (!it.isEmpty()) it else legacyFavorites("v2-empty")
            },
            onFailure = {
                if (it is CancellationException) throw it
                if (it is HttpException && it.code() !in setOf(401, 403, 404)) throw it
                legacyFavorites("v2-${it.safeReason()}")
            },
        )
    suspend fun playlist(id: String): TidalPlaylist? = service.playlist(id, countryCode).dataObjects().firstOrNull()?.toPlaylist()
    suspend fun playlistTracks(id: String): List<TidalTrack> {
        val root = try {
            service.playlistItems(id, countryCode, include = "items,items.albums")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.d(API_LOG_TAG, "playlist items expanded include failed reason=${t.safeReason()}")
            service.playlistItems(id, countryCode)
        }
        val includedByKey = root.includedObjects().associateBy { it.resourceKey() }
        val tracks = root.allResourceObjects().mapNotNull { it.toTrack(includedByKey) }.distinctBy { it.id }
        if (tracks.isNotEmpty() && tracks.none { !it.artworkUrl.isNullOrBlank() }) {
            Log.d(API_LOG_TAG, "playlist tracks art missing tracks=${tracks.size} payloadImageKeys=${root.imageKeySummary()} imageValueCount=${root.imageValueCount()}")
        }
        return tracks
    }
    suspend fun album(id: String): TidalAlbum? = try {
        service.album(id, countryCode).dataObjects().firstOrNull()?.toAlbum()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.d(API_LOG_TAG, "album v2 failed reason=${t.safeReason()}")
        legacyAlbum(id)
    }?.let { v2Album ->
        if (v2Album.artist.isNotBlank() && !v2Album.artworkUrl.isNullOrBlank()) {
            v2Album
        } else {
            val legacyAlbum = legacyAlbum(id)
            v2Album.copy(
                artist = v2Album.artist.ifBlank { legacyAlbum?.artist.orEmpty() },
                artworkUrl = v2Album.artworkUrl ?: legacyAlbum?.artworkUrl,
            )
        }
    }
    suspend fun albumTracks(id: String): List<TidalTrack> {
        val v2Tracks = try {
            service.albumItems(id, countryCode).allResourceObjects().mapNotNull { it.toTrack() }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.d(API_LOG_TAG, "album tracks v2 failed reason=${t.safeReason()}")
            emptyList()
        }
        if (v2Tracks.isEmpty()) return legacyAlbumTracks(id)
        if (v2Tracks.all { it.artist.isNotBlank() && !it.artworkUrl.isNullOrBlank() }) return v2Tracks
        val legacyTracks = runCatching { legacyAlbumTracks(id) }.getOrDefault(emptyList())
        if (legacyTracks.isEmpty()) return v2Tracks
        val legacyById = legacyTracks.associateBy { it.id }
        return v2Tracks.map { track ->
            val legacy = legacyById[track.id]
            track.copy(
                artist = track.artist.ifBlank { legacy?.artist.orEmpty() },
                album = track.album.ifBlank { legacy?.album.orEmpty() },
                artworkUrl = track.artworkUrl ?: legacy?.artworkUrl,
                durationMs = track.durationMs.takeIf { it > 0L } ?: legacy?.durationMs ?: 0L,
            )
        }
    }
    suspend fun track(id: String): TidalTrack? = service.track(id, countryCode).dataObjects().firstOrNull()?.toTrack()
    suspend fun artist(id: String): TidalArtist? = try {
        val v2Artist = service.artist(id, countryCode).dataObjects().firstOrNull()?.toArtist()
        if (v2Artist != null && !v2Artist.artworkUrl.isNullOrBlank()) v2Artist else legacyArtist(id) ?: v2Artist
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.d(API_LOG_TAG, "artist v2 failed reason=${t.safeReason()}")
        legacyArtist(id)
    }

    suspend fun artistContent(id: String): TidalSearchResult {
        val v2Tracks = runCatching {
            val root = service.artistTracks(id, countryCode)
            val includedByKey = root.includedObjects().associateBy { it.resourceKey() }
            root.allResourceObjects().mapNotNull { it.toTrack(includedByKey) }.distinctBy { it.id }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            Log.d(API_LOG_TAG, "artist tracks v2 failed reason=${t.safeReason()}")
            emptyList()
        }
        val tracks = v2Tracks.ifEmpty { legacyArtistTopTracks(id) }
        val v2Albums = runCatching {
            service.artistAlbums(id, countryCode).allResourceObjects().mapNotNull { it.toAlbum() }.distinctBy { it.id }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            Log.d(API_LOG_TAG, "artist albums v2 failed reason=${t.safeReason()}")
            emptyList()
        }
        val albums = v2Albums.ifEmpty { legacyArtistAlbums(id) }
        return TidalSearchResult(tracks = tracks, albums = albums)
    }

    suspend fun homeSections(): List<TidalSection> {
        val result = search("TIDAL")
        return listOf(TidalSection("Search", result.tracks.take(10)))
    }

    suspend fun discoverSections(): List<TidalDiscoverSection> {
        val dedicated = listOfNotNull(
            userMixSection(
                title = "Discovery Mix",
                subtitle = "Personalized recommendations",
                load = { service.userDiscoveryMixes(countryCode, locale) },
                loadItems = { id -> service.userDiscoveryMixItems(id, countryCode, locale) },
            ),
            userMixSection(
                title = "Daily Mixes",
                subtitle = "Based on your listening",
                load = { service.userDailyMixes(countryCode, locale) },
                loadItems = { id -> service.userDailyMixItems(id, countryCode, locale) },
            ),
            userMixSection(
                title = "New for You",
                subtitle = "Personalized new releases",
                load = { service.userNewReleaseMixes(countryCode, locale) },
                loadItems = { id -> service.userNewReleaseMixItems(id, countryCode, locale) },
            ),
        )
        if (dedicated.size == 3) return dedicated

        val fallback = userRecommendationSections()
        if (fallback.isEmpty()) return dedicated
        val dedicatedTitles = dedicated.map { it.title }.toSet()
        return dedicated + fallback.filterNot { it.title in dedicatedTitles }
    }

    private suspend fun userMixSection(
        title: String,
        subtitle: String,
        load: suspend () -> JsonElement,
        loadItems: suspend (String) -> JsonElement,
    ): TidalDiscoverSection? {
        val root = try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is HttpException && t.code() in setOf(401, 403, 404)) {
                Log.d(API_LOG_TAG, "discover v2 unavailable title=$title reason=${t.safeReason()}")
                return null
            }
            Log.d(API_LOG_TAG, "discover v2 failed title=$title reason=${t.safeReason()}")
            throw t
        }
        val direct = parseSearch(root)
        val result = if (!direct.isEmpty()) {
            direct
        } else {
            root.dataObjects()
                .mapNotNull { it.id }
                .distinct()
                .mapNotNull { id ->
                    try {
                        parseSearch(loadItems(id))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        if (t is HttpException && t.code() in setOf(401, 403)) throw t
                        Log.d(API_LOG_TAG, "discover relationship failed title=$title reason=${t.safeReason()}")
                        null
                    }
                }
                .mergeSearchResults()
        }
        Log.d(API_LOG_TAG, "discover v2 ok title=$title tracks=${result.tracks.size} albums=${result.albums.size} playlists=${result.playlists.size}")
        return result.takeUnless { it.isEmpty() }?.let { TidalDiscoverSection(title, it, subtitle) }
    }

    private suspend fun userRecommendationSections(): List<TidalDiscoverSection> {
        // Official but deprecated compatibility fallback for current device-flow tokens, which
        // do not include recommendations.read and can receive 404s from dedicated mix resources.
        val root = try {
            service.userRecommendations("me", countryCode, locale)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is HttpException && t.code() == 404) {
                Log.d(API_LOG_TAG, "discover recommendations empty reason=http-404")
                return emptyList()
            }
            Log.d(API_LOG_TAG, "discover recommendations failed reason=${t.safeReason()}")
            throw t
        }
        val sections = parseUserRecommendationSections(root)
        Log.d(API_LOG_TAG, "discover recommendations ok sections=${sections.size} playlists=${sections.sumOf { it.result.playlists.size }} art=${sections.sumOf { section -> section.result.playlists.count { !it.artworkUrl.isNullOrBlank() } }} payloadImageKeys=${root.imageKeySummary()} imageValueCount=${root.imageValueCount()}")
        return sections
    }

    private fun parseUserRecommendationSections(element: JsonElement): List<TidalDiscoverSection> {
        val relationships = element.dataObjects().firstOrNull()?.get("relationships") as? JsonObject ?: return emptyList()
        val includedByKey = element.includedObjects().associateBy { it.resourceKey() }
        return listOfNotNull(
            recommendationSection(
                title = "Discovery Mix",
                subtitle = "Personalized mix playlist",
                relationshipName = "discoveryMixes",
                relationships = relationships,
                includedByKey = includedByKey,
            ),
            recommendationSection(
                title = "Daily Mixes",
                subtitle = "Based on your listening",
                relationshipName = "myMixes",
                relationships = relationships,
                includedByKey = includedByKey,
            ),
            recommendationSection(
                title = "New for You",
                subtitle = "Personalized new release mix",
                relationshipName = "newArrivalMixes",
                relationships = relationships,
                includedByKey = includedByKey,
            ),
        )
    }

    private fun recommendationSection(
        title: String,
        subtitle: String,
        relationshipName: String,
        relationships: JsonObject,
        includedByKey: Map<String, JsonObject>,
    ): TidalDiscoverSection? {
        val resources = relationships.relationshipResourceKeys(relationshipName)
            .mapNotNull { includedByKey[it] }
        val playlists = resources
            .mapNotNull { it.toPlaylist() }
            .distinctBy { it.id }
        if (playlists.isNotEmpty() && playlists.none { !it.artworkUrl.isNullOrBlank() }) {
            Log.d(API_LOG_TAG, "discover playlist art absent section=$title count=${playlists.size} attrKeys=${resources.map { it.attributes.keysSummary() }.distinct().take(3).joinToString(",")} imageKeys=${JsonArray(resources).imageKeySummary()}")
        }
        return playlists.takeIf { it.isNotEmpty() }
            ?.let { TidalDiscoverSection(title, TidalSearchResult(playlists = it), subtitle) }
    }

    private fun parseSearch(element: JsonElement): TidalSearchResult {
        val resources = element.allResourceObjects()
        return TidalSearchResult(
            tracks = resources.mapNotNull { it.toTrack() }.distinctBy { it.id },
            albums = resources.mapNotNull { it.toAlbum() }.distinctBy { it.id },
            artists = resources.mapNotNull { it.toArtist() }.distinctBy { it.id },
            playlists = resources.mapNotNull { it.toPlaylist() }.distinctBy { it.id },
        )
    }

    private suspend fun legacySearch(query: String, reason: String): TidalSearchResult {
        Log.d(API_LOG_TAG, "search v1 fallback reason=$reason queryChars=${query.length}")
        val clientId = authRepository.getClientIdForApi()
        return runCatching { parseLegacySearch(legacyService.search(query, countryCode, token = clientId)) }
            .recoverCatching {
                Log.d(API_LOG_TAG, "search v1 failed reason=${it.safeReason()}, trying desktop")
                parseLegacySearch(desktopService.search("https://listen.tidal.com/v1/search", query, countryCode))
            }
            .onFailure { Log.d(API_LOG_TAG, "search desktop failed reason=${it.safeReason()}") }
            .getOrThrow()
            .also { Log.d(API_LOG_TAG, "search v1 ok tracks=${it.tracks.size}") }
    }

    private fun parseLegacySearch(element: JsonElement): TidalSearchResult {
        val root = element as? JsonObject ?: return TidalSearchResult()
        return TidalSearchResult(
            tracks = root.legacyItems("tracks").mapNotNull { it.toLegacyTrack() }.distinctBy { it.id },
            albums = root.legacyItems("albums").mapNotNull { it.toLegacyAlbum() }.distinctBy { it.id },
            artists = root.legacyItems("artists").mapNotNull { it.toLegacyArtist() }.distinctBy { it.id },
            playlists = root.legacyItems("playlists").mapNotNull { it.toLegacyPlaylist() }.distinctBy { it.id },
        )
    }

    private suspend fun legacyFavorites(reason: String): TidalSearchResult {
        Log.d(API_LOG_TAG, "library v1 fallback reason=$reason")
        val userId = authRepository.accountInfo.first()?.userId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("TIDAL account unavailable")
        val clientId = authRepository.getClientIdForApi()
        return try {
            TidalSearchResult(
                tracks = parseLegacyTracks(legacyService.favoriteTracks(userId, countryCode, token = clientId)),
                albums = parseLegacyFavoriteObjects(legacyService.favoriteAlbums(userId, countryCode, token = clientId))
                    .mapNotNull { it.toLegacyAlbum() }
                    .distinctBy { it.id },
                artists = parseLegacyFavoriteObjects(legacyService.favoriteArtists(userId, countryCode, token = clientId))
                    .mapNotNull { it.toLegacyArtist() }
                    .distinctBy { it.id },
                playlists = parseLegacyFavoriteObjects(legacyService.favoritePlaylists(userId, countryCode, token = clientId))
                    .mapNotNull { it.toLegacyPlaylist() }
                    .distinctBy { it.id },
            ).also {
                Log.d(API_LOG_TAG, "library v1 ok tracks=${it.tracks.size} albums=${it.albums.size} artists=${it.artists.size} playlists=${it.playlists.size}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.d(API_LOG_TAG, "library v1 failed reason=${t.safeReason()}")
            throw t
        }
    }

    private suspend fun legacyAlbum(id: String): TidalAlbum? {
        val clientId = authRepository.getClientIdForApi()
        return runCatching { (legacyService.album(id, countryCode, token = clientId) as? JsonObject)?.toLegacyAlbum() }
            .recoverCatching {
                Log.d(API_LOG_TAG, "album v1 failed reason=${it.safeReason()}, trying desktop")
                (desktopService.album("https://listen.tidal.com/v1/albums/$id", countryCode) as? JsonObject)?.toLegacyAlbum()
            }
            .onFailure { Log.d(API_LOG_TAG, "album desktop failed reason=${it.safeReason()}") }
            .getOrNull()
    }

    private suspend fun legacyAlbumTracks(id: String): List<TidalTrack> {
        Log.d(API_LOG_TAG, "album tracks v1 fallback")
        val clientId = authRepository.getClientIdForApi()
        return runCatching { parseLegacyTracks(legacyService.albumTracks(id, countryCode, token = clientId)) }
            .recoverCatching {
                Log.d(API_LOG_TAG, "album tracks v1 failed reason=${it.safeReason()}, trying desktop")
                parseLegacyTracks(desktopService.albumTracks("https://listen.tidal.com/v1/albums/$id/tracks", countryCode))
            }
            .onFailure { Log.d(API_LOG_TAG, "album tracks desktop failed reason=${it.safeReason()}") }
            .getOrThrow()
            .also { Log.d(API_LOG_TAG, "album tracks ok count=${it.size}") }
    }

    private suspend fun legacyArtist(id: String): TidalArtist? {
        val clientId = authRepository.getClientIdForApi()
        return runCatching { (legacyService.artist(id, countryCode, token = clientId) as? JsonObject)?.toLegacyArtist() }
            .recoverCatching {
                Log.d(API_LOG_TAG, "artist v1 failed reason=${it.safeReason()}, trying desktop")
                (desktopService.artist("https://listen.tidal.com/v1/artists/$id", countryCode) as? JsonObject)?.toLegacyArtist()
            }
            .onFailure { Log.d(API_LOG_TAG, "artist desktop failed reason=${it.safeReason()}") }
            .getOrNull()
    }

    private suspend fun legacyArtistTopTracks(id: String): List<TidalTrack> {
        val clientId = authRepository.getClientIdForApi()
        return runCatching { parseLegacyTracks(legacyService.artistTopTracks(id, countryCode, token = clientId)) }
            .recoverCatching {
                Log.d(API_LOG_TAG, "artist top tracks v1 failed reason=${it.safeReason()}, trying desktop")
                parseLegacyTracks(desktopService.artistTopTracks("https://listen.tidal.com/v1/artists/$id/toptracks", countryCode))
            }
            .onFailure { Log.d(API_LOG_TAG, "artist top tracks desktop failed reason=${it.safeReason()}") }
            .getOrDefault(emptyList())
            .also { Log.d(API_LOG_TAG, "artist top tracks ok count=${it.size}") }
    }

    private suspend fun legacyArtistAlbums(id: String): List<TidalAlbum> {
        val clientId = authRepository.getClientIdForApi()
        return runCatching {
            parseLegacyObjects(legacyService.artistAlbums(id, countryCode, token = clientId)).mapNotNull { it.toLegacyAlbum() }.distinctBy { it.id }
        }.recoverCatching {
            Log.d(API_LOG_TAG, "artist albums v1 failed reason=${it.safeReason()}, trying desktop")
            parseLegacyObjects(desktopService.artistAlbums("https://listen.tidal.com/v1/artists/$id/albums", countryCode)).mapNotNull { it.toLegacyAlbum() }.distinctBy { it.id }
        }.onFailure {
            Log.d(API_LOG_TAG, "artist albums desktop failed reason=${it.safeReason()}")
        }.getOrDefault(emptyList())
            .also { Log.d(API_LOG_TAG, "artist albums ok count=${it.size}") }
    }

    private fun parseLegacyTracks(element: JsonElement): List<TidalTrack> {
        val root = element as? JsonObject
        val albumArtist = root?.legacyAlbumArtist().orEmpty()
        val items = when {
            root == null -> (element as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            root["items"] is JsonArray -> (root["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            else -> root.legacyItems("tracks")
        }
        return items.mapNotNull { it.legacyTrackObject().toLegacyTrack() }
            .map { track -> if (track.artist.isBlank() && albumArtist.isNotBlank()) track.copy(artist = albumArtist) else track }
            .distinctBy { it.id }
    }

    private fun parseLegacyFavoriteObjects(element: JsonElement): List<JsonObject> = parseLegacyObjects(element)

    private fun parseLegacyObjects(element: JsonElement): List<JsonObject> {
        val root = element as? JsonObject
        return when {
            root == null -> (element as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            root["items"] is JsonArray -> (root["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            else -> emptyList()
        }
    }
}

private fun TidalSearchResult.isEmpty(): Boolean =
    tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

private fun List<TidalSearchResult>.mergeSearchResults(): TidalSearchResult = TidalSearchResult(
    tracks = flatMap { it.tracks }.distinctBy { it.id },
    albums = flatMap { it.albums }.distinctBy { it.id },
    artists = flatMap { it.artists }.distinctBy { it.id },
    playlists = flatMap { it.playlists }.distinctBy { it.id },
)

private fun JsonElement.allResourceObjects(): List<JsonObject> = dataObjects() + includedObjects()

private fun JsonElement.dataObjects(): List<JsonObject> {
    val root = this as? JsonObject ?: return emptyList()
    return when (val data = root["data"]) {
        is JsonArray -> data.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(data)
        else -> emptyList()
    }
}

private fun JsonElement.includedObjects(): List<JsonObject> {
    val root = this as? JsonObject ?: return emptyList()
    return (root["included"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}

private fun JsonObject.toTrack(): TidalTrack? {
    return toTrack(emptyMap())
}

private fun JsonObject.toTrack(includedByKey: Map<String, JsonObject>): TidalTrack? {
    if (!typeMatches("track", "tracks")) return null
    val attrs = attributes
    val album = relationshipResourceKeys("album", "albums")
        .firstNotNullOfOrNull { includedByKey[it] }
    val albumAttrs = album?.attributes
    val title = attrs.string("title", "name") ?: return null
    return TidalTrack(
        id = id ?: return null,
        title = title,
        artist = attrs.string("artistName", "artist", "artists")
            ?: albumAttrs?.string("artistName", "artist")
            ?: "",
        album = attrs.string("albumTitle", "album") ?: albumAttrs?.string("title", "name") ?: "",
        artworkUrl = attrs.imageUrl(320) ?: albumAttrs?.imageUrl(320) ?: album?.imageUrl(320),
        durationMs = attrs.long("duration", "durationMs")?.let { if (it < 10_000) it * 1000L else it } ?: 0L,
    )
}

private fun JsonObject.toAlbum(): TidalAlbum? {
    if (!typeMatches("album", "albums")) return null
    val attrs = attributes
    return TidalAlbum(
        id = id ?: return null,
        title = attrs.string("title", "name") ?: return null,
        artist = attrs.string("artistName", "artist") ?: "",
        artworkUrl = attrs.imageUrl(320),
    )
}

private fun JsonObject.toArtist(): TidalArtist? {
    if (!typeMatches("artist", "artists")) return null
    val attrs = attributes
    return TidalArtist(
        id = id ?: return null,
        name = attrs.string("name", "title") ?: return null,
        artworkUrl = attrs.imageUrl(320),
    )
}

private fun JsonObject.toPlaylist(): TidalPlaylist? {
    if (!typeMatches("playlist", "playlists")) return null
    val attrs = attributes
    return TidalPlaylist(
        id = id ?: return null,
        title = attrs.string("title", "name") ?: return null,
        creator = attrs.string("creatorName", "ownerName", "creator") ?: "",
        artworkUrl = attrs.imageUrl(320),
    )
}

private fun JsonObject.toLegacyTrack(): TidalTrack? {
    val resource = legacyWrappedObject("track", "item")
    val album = (resource["album"] as? JsonObject)?.legacyWrappedObject("album", "item")
    val id = resource.primitive("id")?.contentOrNull ?: return null
    val title = resource.string("title", "name") ?: return null
    return TidalTrack(
        id = id,
        title = title,
        artist = (resource["artist"] as? JsonObject)?.string("name")
            ?: resource.legacyArtistNames()
            ?: resource.string("artistName", "artist")
            ?: album?.legacyAlbumArtist()
            ?: "",
        album = album?.string("title", "name") ?: resource.string("albumTitle", "album") ?: "",
        artworkUrl = album?.imageUrl(320) ?: resource.imageUrl(320),
        durationMs = resource.long("duration", "durationMs")?.let { if (it < 10_000) it * 1000L else it } ?: 0L,
    )
}

private fun JsonObject.toLegacyAlbum(): TidalAlbum? {
    val resource = legacyWrappedObject("album", "item")
    val id = resource.primitive("id")?.contentOrNull ?: return null
    val title = resource.string("title", "name") ?: return null
    val artist = resource.legacyAlbumArtist()
    val artworkUrl = resource.imageUrl(320)
    if (artist.isBlank() || artworkUrl.isNullOrBlank()) {
        Log.d(API_LOG_TAG, "album metadata parse incomplete art=${!artworkUrl.isNullOrBlank()} artist=${artist.isNotBlank()} keys=${resource.keysSummary()}")
    }
    return TidalAlbum(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
    )
}

private fun JsonObject.toLegacyArtist(): TidalArtist? {
    val resource = legacyWrappedObject("artist", "item")
    val id = resource.primitive("id")?.contentOrNull ?: return null
    val name = resource.string("name", "title") ?: return null
    return TidalArtist(id = id, name = name, artworkUrl = resource.imageUrl(320))
}

private fun JsonObject.toLegacyPlaylist(): TidalPlaylist? {
    val resource = legacyWrappedObject("playlist", "item")
    val id = resource.primitive("uuid")?.contentOrNull ?: resource.primitive("id")?.contentOrNull ?: return null
    val title = resource.string("title", "name") ?: return null
    return TidalPlaylist(
        id = id,
        title = title,
        creator = (resource["creator"] as? JsonObject)?.string("name") ?: resource.string("creatorName", "ownerName", "creator") ?: "",
        artworkUrl = resource.imageUrl(320),
    )
}

private val JsonObject.id: String? get() = primitive("id")?.contentOrNull
private val JsonObject.attributes: JsonObject get() = this["attributes"] as? JsonObject ?: this
private fun JsonObject.typeMatches(vararg expected: String): Boolean = primitive("type")?.contentOrNull?.lowercase() in expected.map { it.lowercase() }
private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive
private fun JsonObject.resourceKey(): String = "${primitive("type")?.contentOrNull.orEmpty()}:${id.orEmpty()}"

private fun JsonObject.relationshipResourceKeys(vararg names: String): List<String> {
    val relationships = this["relationships"] as? JsonObject ?: return emptyList()
    return names.flatMap { relationships.relationshipResourceKeys(it) }
}

private fun JsonObject.relationshipResourceKeys(name: String): List<String> {
    val relationship = this[name] as? JsonObject ?: return emptyList()
    return when (val data = relationship["data"]) {
        is JsonArray -> data.mapNotNull { (it as? JsonObject)?.resourceKey()?.takeIf { key -> key != ":" } }
        is JsonObject -> listOfNotNull(data.resourceKey().takeIf { it != ":" })
        else -> emptyList()
    }
}

private fun JsonObject.legacyItems(name: String): List<JsonObject> {
    val bucket = this[name] as? JsonObject ?: return emptyList()
    return (bucket["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}

private fun JsonObject.legacyArtistNames(): String? = (this["artists"] as? JsonArray)
    ?.mapNotNull { (it as? JsonObject)?.string("name") }
    ?.takeIf { it.isNotEmpty() }
    ?.joinToString(", ")

private fun JsonObject.legacyAlbumArtist(): String =
    (this["artist"] as? JsonObject)?.string("name")
        ?: legacyArtistNames()
        ?: (this["artists"] as? JsonObject)?.string("name")
        ?: (this["album"] as? JsonObject)?.legacyWrappedObject("album", "item")?.legacyAlbumArtist()
        ?: string("artistName", "artistNameInBaseLocale", "albumArtist", "artist")
        ?: ""

private fun JsonObject.legacyTrackObject(): JsonObject =
    legacyWrappedObject("track", "item")

private fun JsonObject.legacyWrappedObject(vararg names: String): JsonObject {
    names.forEach { name ->
        (this[name] as? JsonObject)?.let { return it }
    }
    return this
}

private fun JsonObject.string(vararg names: String): String? {
    names.forEach { name ->
        val value = primitive(name)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject.long(vararg names: String): Long? {
    names.forEach { name ->
        primitive(name)?.contentOrNull?.toLongOrNull()?.let { return it }
    }
    return null
}

private fun JsonObject.imageUrl(size: Int = 320): String? {
    directImageUrl(size)?.let { return it }
    listOf("album", "item", "track", "artist", "playlist", "image", "picture").forEach { name ->
        (this[name] as? JsonObject)?.directImageUrl(size)?.let { return it }
    }
    return null
}

private fun JsonObject.directImageUrl(size: Int = 320): String? {
    string("imageUrl", "artworkUrl", "coverUrl")?.let { return it }
    string("coverArt", "cover", "image", "imageId", "picture", "squareImage", "smallImage", "largeImage")
        ?.takeIf { it.isNotBlank() }
        ?.let { return if (it.startsWith("http", ignoreCase = true)) it else tidalResourceImageUrl(it, size) }
    string("url")?.takeIf { it.looksLikeImageUrl() }?.let { return it }
    val imageLinks = this["imageLinks"] as? JsonArray
    imageLinks?.mapNotNull { it as? JsonObject }
        ?.sortedByDescending { it.long("width", "height") ?: 0L }
        ?.firstOrNull { (it.long("width") ?: 0L) <= 640L }
        ?.string("href", "url")
        ?.let { return it }
    imageLinks?.mapNotNull { (it as? JsonObject)?.string("href", "url") }?.firstOrNull()?.let { return it }
    val images = this["images"] as? JsonArray
    images?.mapNotNull { it as? JsonObject }
        ?.sortedByDescending { it.long("width", "height") ?: 0L }
        ?.firstOrNull()
        ?.string("href", "url", "imageUrl")
        ?.let { return it }
    images?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.firstOrNull()?.let { return it }
    return null
}

private fun String.looksLikeImageUrl(): Boolean {
    val lower = lowercase()
    return lower.contains("resources.tidal.com/images/") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp")
}

private fun tidalResourceImageUrl(id: String, size: Int): String {
    val normalized = id.trim().replace('-', '/')
    val supported = listOf(80, 160, 320, 640, 1280).firstOrNull { it >= size } ?: 320
    return "https://resources.tidal.com/images/$normalized/${supported}x${supported}.jpg"
}

private fun JsonObject.keysSummary(): String =
    keys.sorted().take(16).joinToString("|")

private fun JsonElement.imageKeySummary(): String =
    imageKeys().sorted().take(24).joinToString("|").ifBlank { "none" }

private fun JsonElement.imageKeys(): Set<String> = when (this) {
    is JsonObject -> keys.filter { it.looksLikeArtworkKey() }
        .toSet() + values.flatMap { it.imageKeys() }
    is JsonArray -> flatMap { it.imageKeys() }.toSet()
    else -> emptySet()
}

private fun JsonElement.imageValueCount(): Int = when (this) {
    is JsonObject -> values.sumOf { it.imageValueCount() }
    is JsonArray -> sumOf { it.imageValueCount() }
    is JsonPrimitive -> if (contentOrNull?.looksLikeImageUrl() == true) 1 else 0
}

private fun String.looksLikeArtworkKey(): Boolean {
    val lower = lowercase()
    return lower.contains("image") ||
        lower.contains("artwork") ||
        lower.contains("picture") ||
        lower.contains("thumbnail") ||
        lower == "cover" ||
        lower.endsWith("cover") ||
        lower.contains("coverart")
}

private fun Throwable.safeReason(): String = when (this) {
    is HttpException -> "http-${code()}"
    else -> this::class.java.simpleName
}

private const val JSON_API_ACCEPT = "application/vnd.api+json"
private const val API_LOG_TAG = "Untidy/API"

