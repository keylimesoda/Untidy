package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlaybackQueueStore {
    private const val MAX_QUEUE_TRACKS = 100
    private val json = Json { ignoreUnknownKeys = true }
    private val queues = ConcurrentHashMap<String, List<TidalTrack>>()

    fun playableTracks(tracks: List<TidalTrack>): List<TidalTrack> = tracks.asSequence()
        .filter { it.id.isNotBlank() }
        .take(MAX_QUEUE_TRACKS)
        .toList()

    fun put(tracks: List<TidalTrack>): String {
        val playableTracks = playableTracks(tracks)
        if (playableTracks.isEmpty()) return ""
        val id = UUID.randomUUID().toString()
        queues[id] = playableTracks
        return id
    }

    fun get(id: String): List<TidalTrack> = queues[id].orEmpty()

    fun payloadFor(tracks: List<TidalTrack>): String = json.encodeToString(playableTracks(tracks))

    fun fromPayload(payload: String): List<TidalTrack> = runCatching {
        playableTracks(json.decodeFromString<List<TidalTrack>>(payload))
    }.getOrDefault(emptyList())
}
