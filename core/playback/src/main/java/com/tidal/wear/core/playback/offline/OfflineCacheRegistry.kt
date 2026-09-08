@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback.offline

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache

/** Media3 permits one SimpleCache per directory, shared by playback and a repair/download. */
internal object OfflineCacheRegistry {
    private data class Entry(val cache: SimpleCache, var references: Int)
    private val entries = mutableMapOf<String, Entry>()

    @Synchronized fun acquire(context: Context, trackId: String): SimpleCache {
        val directory = context.offlineTrackCacheDir(trackId)
        val key = directory.absolutePath
        entries[key]?.let { it.references++; return it.cache }
        return SimpleCache(directory, NoOpCacheEvictor(), StandaloneDatabaseProvider(context.applicationContext))
            .also { entries[key] = Entry(it, 1) }
    }

    @Synchronized fun release(context: Context, trackId: String) {
        val key = context.offlineTrackCacheDir(trackId).absolutePath
        val entry = entries[key] ?: return
        if (--entry.references == 0) {
            entries.remove(key)
            entry.cache.release()
        }
    }
}
