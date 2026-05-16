package com.tidal.wear.core.playback

import com.tidal.wear.core.model.TidalTrack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlaybackQueueStore {
    private const val MAX_QUEUE_TRACKS = 100
    private val queues = ConcurrentHashMap<String, List<TidalTrack>>()

    fun put(tracks: List<TidalTrack>): String {
        val playableTracks = tracks.asSequence()
            .filter { it.id.isNotBlank() }
            .take(MAX_QUEUE_TRACKS)
            .toList()
        val id = UUID.randomUUID().toString()
        queues[id] = playableTracks
        return id
    }

    fun get(id: String): List<TidalTrack> = queues[id].orEmpty()
}
