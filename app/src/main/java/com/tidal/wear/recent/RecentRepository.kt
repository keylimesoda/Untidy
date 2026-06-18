package com.tidal.wear.recent

import android.content.Context
import android.content.SharedPreferences
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

private const val RECENT_PREFS = "untidy_recent_history"
private const val RECENT_ITEMS = "items"
private const val RECENT_CAP = 20

enum class RecentItemType(val wireName: String) {
    Track("track"),
    Album("album"),
    Playlist("playlist"),
    ;

    companion object {
        fun fromWireName(value: String): RecentItemType? = entries.firstOrNull { it.wireName == value }
    }
}

data class RecentItem(
    val type: RecentItemType,
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val lastPlayedAt: Long,
    val album: String = "",
    val albumId: String = "",
    val artistId: String = "",
)

object RecentHistory {
    fun record(existing: List<RecentItem>, item: RecentItem): List<RecentItem> {
        if (item.id.isBlank() || item.title.isBlank()) return existing
        return (listOf(item) + existing.filterNot { it.type == item.type && it.id == item.id })
            .take(RECENT_CAP)
    }
}

class RecentRepository(context: Context) {
    private val prefs = context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)

    val items: Flow<List<RecentItem>> = callbackFlow {
        trySend(load())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RECENT_ITEMS) trySend(load())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun load(): List<RecentItem> = decodeItems(prefs.getString(RECENT_ITEMS, null).orEmpty())

    fun recordTrack(track: TidalTrack, now: Long = System.currentTimeMillis()) {
        record(
            RecentItem(
                type = RecentItemType.Track,
                id = track.id,
                title = track.title,
                subtitle = track.artist.ifBlank { "Track" },
                artworkUrl = track.artworkUrl,
                lastPlayedAt = now,
                album = track.album,
                albumId = track.albumId,
                artistId = track.artistId,
            ),
        )
    }

    fun recordAlbum(album: TidalAlbum, now: Long = System.currentTimeMillis()) {
        record(
            RecentItem(
                type = RecentItemType.Album,
                id = album.id,
                title = album.title,
                subtitle = album.artist.ifBlank { "Album" },
                artworkUrl = album.artworkUrl,
                lastPlayedAt = now,
            ),
        )
    }

    fun recordPlaylist(playlist: TidalPlaylist, now: Long = System.currentTimeMillis()) {
        record(
            RecentItem(
                type = RecentItemType.Playlist,
                id = playlist.id,
                title = playlist.title,
                subtitle = playlist.creator.ifBlank { "Playlist" },
                artworkUrl = playlist.artworkUrl,
                lastPlayedAt = now,
            ),
        )
    }

    fun clear() {
        prefs.edit().remove(RECENT_ITEMS).apply()
    }

    private fun record(item: RecentItem) {
        val next = RecentHistory.record(load(), item)
        prefs.edit().putString(RECENT_ITEMS, encodeItems(next)).apply()
    }
}

fun RecentItem.toTrack(): TidalTrack = TidalTrack(
    id = id,
    title = title,
    artist = subtitle.takeIf { it != "Track" }.orEmpty(),
    album = album,
    artworkUrl = artworkUrl,
    albumId = albumId,
    artistId = artistId,
)

private fun encodeItems(items: List<RecentItem>): String = JSONArray().apply {
    items.forEach { item ->
        put(JSONObject().apply {
            put("type", item.type.wireName)
            put("id", item.id)
            put("title", item.title)
            put("subtitle", item.subtitle)
            put("artworkUrl", item.artworkUrl)
            put("lastPlayedAt", item.lastPlayedAt)
            put("album", item.album)
            put("albumId", item.albumId)
            put("artistId", item.artistId)
        })
    }
}.toString()

private fun decodeItems(raw: String): List<RecentItem> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val type = RecentItemType.fromWireName(json.optString("type")) ?: continue
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = json.optString("title").takeIf { it.isNotBlank() } ?: continue
            add(
                RecentItem(
                    type = type,
                    id = id,
                    title = title,
                    subtitle = json.optString("subtitle"),
                    artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
                    lastPlayedAt = json.optLong("lastPlayedAt"),
                    album = json.optString("album"),
                    albumId = json.optString("albumId"),
                    artistId = json.optString("artistId"),
                ),
            )
        }
    }.take(RECENT_CAP)
}.getOrDefault(emptyList())
