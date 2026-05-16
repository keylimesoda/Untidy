package com.tidal.wear.core.api

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalSection
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.CancellationException
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
}

class TidalApiClient(
    private val authRepository: TidalAuthRepository,
    private val countryCode: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US",
) {
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
    suspend fun favorites(): TidalSearchResult = parseSearch(service.favorites(countryCode))
    suspend fun playlist(id: String): TidalPlaylist? = service.playlist(id, countryCode).dataObjects().firstOrNull()?.toPlaylist()
    suspend fun playlistTracks(id: String): List<TidalTrack> = service.playlistItems(id, countryCode).allResourceObjects().mapNotNull { it.toTrack() }
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
    suspend fun artist(id: String): TidalArtist? = service.artist(id, countryCode).dataObjects().firstOrNull()?.toArtist()

    suspend fun homeSections(): List<TidalSection> {
        val result = search("TIDAL")
        return listOf(TidalSection("Search", result.tracks.take(10)))
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
}

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
    if (!typeMatches("track", "tracks")) return null
    val attrs = attributes
    val title = attrs.string("title", "name") ?: return null
    return TidalTrack(
        id = id ?: return null,
        title = title,
        artist = attrs.string("artistName", "artist", "artists") ?: "",
        album = attrs.string("albumTitle", "album") ?: "",
        artworkUrl = attrs.imageUrl(320),
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
    string("coverArt", "cover", "imageId", "picture", "squareImage")
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

private fun Throwable.safeReason(): String = when (this) {
    is HttpException -> "http-${code()}"
    else -> this::class.java.simpleName
}

private const val JSON_API_ACCEPT = "application/vnd.api+json"
private const val API_LOG_TAG = "Untidy/API"

