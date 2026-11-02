@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.core.playback.offline.OfflinePlaybackDescriptor
import com.tidal.wear.core.playback.offline.OfflineCacheRegistry
import com.tidal.wear.core.playback.offline.markOfflineTrackRepairRequired
import com.tidal.wear.core.playback.offline.OfflinePlaybackPlan
import com.tidal.wear.core.playback.offline.offlinePlaybackDescriptor
import com.tidal.wear.core.playback.offline.offlinePlaybackPlan
import com.tidal.wear.core.playback.offline.offlineTrackCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class DirectManifestPlaybackBackend(
    context: Context,
    private val scope: CoroutineScope,
    private val authRepository: TidalAuthRepository,
) : PlaybackBackend {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val _events = MutableSharedFlow<PlaybackBackendEvent>(extraBufferCapacity = 32)
    private val offlineCaches = ConcurrentHashMap<String, SimpleCache>()
    private var audioPreset = AudioPreset.BatterySaver
    private var currentManifest: ResolvedManifest? = null
    private val manifestRequests = ConcurrentHashMap<String, ManifestRequest>()
    private val activeManifestCalls = ConcurrentHashMap<String, Call>()
    private var queueWindow: PlaybackQueueWindow? = null
    private var queueGeneration = 0L
    private var appendedMediaItemCount = 0
    private var lookAheadJob: Job? = null
    private val resolvedManifestsByPlayerIndex = mutableMapOf<Int, ResolvedManifest>()
    // Rebuilt (never reconfigured in place) when the stall sentinel proves this device's
    // offload pipeline is broken: renderer offload settings only bind at player creation.
    private val lastOffloadedPlayback = java.util.concurrent.atomic.AtomicBoolean(false)
    private var rebuildsForCurrentItem = 0
    private var player: ExoPlayer = buildPlayer()

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
        .also { exo ->
            lastOffloadedPlayback.set(false)
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    playbackAudioOffloadPreferences(AudioOffloadSuspension.isSuspended(appContext)),
                )
                .build()
            exo.addAudioOffloadListener(object : ExoPlayer.AudioOffloadListener {
                override fun onOffloadedPlayback(offloadedPlayback: Boolean) {
                    lastOffloadedPlayback.set(offloadedPlayback)
                    Log.d(DIRECT_LOG_TAG, "audio offloaded=$offloadedPlayback")
                }
                override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
                    Log.d(DIRECT_LOG_TAG, "audio sleepingForOffload=$sleepingForOffload")
                }
            })
            exo.setWakeMode(C.WAKE_MODE_NETWORK)
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        currentManifest?.let {
                            _events.tryEmit(
                                PlaybackBackendEvent.MediaEnded(
                                    it.toDiagnosticContext(exo.duration, queueIndexForPlayerIndex(exo.currentMediaItemIndex)),
                                ),
                            )
                        }
                    } else {
                        _events.tryEmit(PlaybackBackendEvent.StateChanged(playbackState.toBackendState(exo.isPlaying)))
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _events.tryEmit(
                        PlaybackBackendEvent.StateChanged(
                            if (isPlaying) PlaybackBackendState.Playing else PlaybackBackendState.NotPlaying,
                        ),
                    )
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Buffered/position continuity is per item; never carry stall history.
                    stallSentinel.reset()
                    rebuildsForCurrentItem = 0
                    val mediaId = mediaItem?.mediaId.orEmpty()
                    // A selected Format belongs to the previous item until ExoPlayer reports the
                    // new item's tracks. Clear it at the transition so consumers cannot display
                    // stale delivered-audio details for the new track.
                    _events.tryEmit(
                        PlaybackBackendEvent.AudioFormatChanged(
                            trackId = mediaId.takeIf { it.isNotBlank() },
                            format = null,
                        ),
                    )
                    currentManifest = mediaId.takeIf { it.isNotBlank() }
                        ?.let { id ->
                            resolvedManifestsByPlayerIndex[exo.currentMediaItemIndex]
                                ?: currentManifest?.takeIf { it.trackId == id }
                                ?: ResolvedManifest.placeholder(id)
                        }
                    currentManifest?.let { manifest ->
                        _events.tryEmit(
                            PlaybackBackendEvent.MediaTransition(
                                manifest.toDiagnosticContext(exo.duration, queueIndexForPlayerIndex(exo.currentMediaItemIndex)),
                            ),
                        )
                    }
                    mediaId.takeIf { it.isNotBlank() }?.let { id ->
                        exo.setWakeMode(if (appContext.isOfflineTrackDownloaded(id)) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
                    }
                    appendLookAheadAfter(exo.currentMediaItemIndex)
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val trackId = exo.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
                    _events.tryEmit(
                        PlaybackBackendEvent.AudioFormatChanged(
                            trackId = trackId,
                            format = tracks.selectedAudioFormat()?.toDeliveredAudioFormat(),
                        ),
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    val id = exo.currentMediaItem?.mediaId
                    val cause = if (id != null && appContext.isOfflineTrackDownloaded(id) &&
                        error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                    ) {
                        appContext.markOfflineTrackRepairRequired(id)
                        IOException("Downloaded track needs repair: local cache cannot be read", error)
                    } else error
                    _events.tryEmit(PlaybackBackendEvent.Error(error.errorCodeName, cause))
                }
            },
        )
    }

    private val stallSentinel = OffloadStallSentinel()
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogTick = object : Runnable {
        override fun run() {
            if (player.playWhenReady && player.isPlaying) {
                val verdict = stallSentinel.observe(
                    OffloadStallSentinel.Sample(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        bufferedMs = player.bufferedPosition,
                        durationMs = player.duration,
                        isPlaying = true,
                        isLoading = player.isLoading,
                        // Consumption semantics only apply while the DSP offload path is
                        // actually active: software position is the renderer clock and a
                        // bandwidth-limited stream legitimately drains a frozen buffer.
                        usesOffload = lastOffloadedPlayback.get(),
                    ),
                )
                if (verdict != OffloadStallSentinel.Stall.None) recoverFromStall(verdict)
            } else {
                stallSentinel.reset()
            }
            watchdogHandler.postDelayed(this, OFFLOAD_WATCHDOG_INTERVAL_MS)
        }
    }

    /**
     * Offload is the battery default, but a wedged offload pipeline must not mean silent
     * playback (UX-021): once the sentinel proves it, force software decoding for the
     * current item and pin this device off offload so every later item starts audible.
     */
    private fun recoverFromStall(stall: OffloadStallSentinel.Stall) {
        if (rebuildsForCurrentItem >= MAX_REBUILDS_PER_ITEM) {
            Log.w(DIRECT_LOG_TAG, "playback stall ($stall) but rebuild budget exhausted for this item; holding")
            return
        }
        rebuildsForCurrentItem += 1
        Log.w(DIRECT_LOG_TAG, "playback stall detected ($stall); rebuilding player without offload")
        AudioOffloadSuspension.suspend(appContext)
        stallSentinel.reset()
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val resumeIndex = player.currentMediaItemIndex
        val resumeAt = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.playWhenReady
        val brokenPlayer = player
        player = buildPlayer().also { fresh ->
            fresh.setMediaItems(items, resumeIndex, resumeAt)
            fresh.playWhenReady = wasPlaying
            fresh.prepare()
        }
        setPreferredAudioOutput(PreferredAudioOutputPreference.get(appContext))
        brokenPlayer.release()
        // The MediaSession binds to a specific Player instance (wrapped by the service's
        // queue forwarding player); after a rebuild it mirrors a RELEASED player unless the
        // service rebinds — that orphaned the now-playing UI mid-queue (EXP-014).
        _events.tryEmit(PlaybackBackendEvent.PlayerReplaced)
        // No handler re-post here: watchdogTick's tail owns the single cadence. Posting
        // from both paths accumulates parallel timers (the exact cause of the rebuild storm).
    }

    init {
        setPreferredAudioOutput(PreferredAudioOutputPreference.get(appContext))
        watchdogHandler.postDelayed(watchdogTick, OFFLOAD_WATCHDOG_INTERVAL_MS)
    }

    override val events: Flow<PlaybackBackendEvent> = _events.asSharedFlow()
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    override val sessionPlayer: Player get() = player

    override fun setAudioPreset(preset: AudioPreset) {
        if (audioPreset != preset) {
            lookAheadJob?.cancel()
            cancelManifestRequests()
        }
        audioPreset = preset
    }

    override suspend fun loadTrack(trackId: String) {
        if (trackId.isBlank()) return
        val item = TidalTrack(id = trackId, title = "TIDAL", artist = "", album = "")
        loadQueue(listOf(item), startIndex = 0)
    }

    override suspend fun loadTrack(track: TidalTrack) {
        if (track.id.isBlank()) return
        loadQueue(listOf(track), startIndex = 0)
    }

    override suspend fun loadQueue(tracks: List<TidalTrack>, startIndex: Int) {
        val queueStartedAt = SystemClock.elapsedRealtime()
        val window = PlaybackQueueResolutionPolicy.window(tracks, startIndex) ?: return
        val generation = ++queueGeneration
        lookAheadJob?.cancel()
        cancelManifestRequestsExcept(generation)
        queueWindow = window
        appendedMediaItemCount = 0
        resolvedManifestsByPlayerIndex.clear()
        runCatching {
            Log.d(DIRECT_LOG_TAG, "loadQueue start size=${window.tracks.size} start=${window.startIndex} tail=${window.tracks.size - window.startIndex} initialManifestCount=${window.initialResolutionTracks.size}")
            val first = resolveMediaSource(window.initialTrack, window.tracks, window.startIndex, generation)
            if (generation != queueGeneration) return
            currentManifest = first.manifest
            player.setWakeMode(if (first.offline) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
            player.setMediaSource(first.source)
            player.prepare()
            appendedMediaItemCount = 1
            resolvedManifestsByPlayerIndex[0] = first.manifest
            Log.d(DIRECT_LOG_TAG, "loadQueue prepared initialManifestCount=${window.initialResolutionTracks.size} elapsedMs=${SystemClock.elapsedRealtime() - queueStartedAt}")
            _events.emit(PlaybackBackendEvent.MediaTransition(first.manifest.toDiagnosticContext(player.duration, window.startIndex)))
            appendLookAheadAfter(playerIndex = 0, generation = generation)
        }.onFailure { error ->
            if (error is CancellationException) {
                Log.d(DIRECT_LOG_TAG, "manifest queue load cancelled start=${window.initialTrack.id}")
            } else {
                Log.e(DIRECT_LOG_TAG, "manifest queue load failed start=${window.initialTrack.id}", error)
                _events.emit(PlaybackBackendEvent.Error(error::class.java.simpleName, error))
            }
        }.getOrThrow()
    }

    override fun setPreferredAudioOutput(deviceId: Int?) {
        val device = deviceId?.let { requestedId ->
            appContext.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.id == requestedId && it.type.isSelectableWearOutputType() }
        }
        if (deviceId != null && device == null) {
            Log.w(DIRECT_LOG_TAG, "preferred output unavailable id=$deviceId")
            player.setPreferredAudioDevice(null)
            PreferredAudioOutputPreference.set(appContext, null)
            return
        }
        // Media3 forwards this to its AudioTrack. It is a preference for Untidy only; Android may
        // still select a different physical route when the requested device disconnects.
        player.setPreferredAudioDevice(device)
        PreferredAudioOutputPreference.set(appContext, device?.id)
        Log.d(DIRECT_LOG_TAG, "preferred output id=${device?.id ?: "system-default"}")
    }

    /**
     * Resolves at most one future manifest. The current item is already prepared when this runs,
     * so a slow next-item request cannot delay first audio. Keeping the source list contiguous
     * lets MediaSession retain ordinary next/previous behaviour for the ready window.
     */
    private fun appendLookAheadAfter(playerIndex: Int, generation: Long = queueGeneration) {
        val window = queueWindow ?: return
        if (generation != queueGeneration || appendedMediaItemCount != playerIndex + 1) return
        val next = window.lookAheadForPlayerIndex(playerIndex) ?: return
        lookAheadJob?.takeIf { it.isActive }?.let { return }
        lookAheadJob = scope.launch {
            runCatching {
                val queueIndex = window.queueIndexForPlayerIndex(playerIndex + 1)
                val resolved = resolveMediaSource(next, window.tracks, queueIndex, generation)
                if (generation != queueGeneration || queueWindow !== window || appendedMediaItemCount != playerIndex + 1) return@launch
                player.addMediaSource(resolved.source)
                resolvedManifestsByPlayerIndex[playerIndex + 1] = resolved.manifest
                appendedMediaItemCount += 1
                Log.d(DIRECT_LOG_TAG, "manifest lookahead appended id=${next.id} playerIndex=${playerIndex + 1} queueIndex=$queueIndex")
            }.onFailure { error ->
                if (generation == queueGeneration) {
                    Log.w(DIRECT_LOG_TAG, "manifest lookahead failed id=${next.id} reason=${error::class.java.simpleName}: ${error.message}")
                }
            }
        }
    }

    private fun queueIndexForPlayerIndex(playerIndex: Int): Int? =
        queueWindow?.trackForPlayerIndex(playerIndex)?.let { queueWindow?.queueIndexForPlayerIndex(playerIndex) }

    override fun prefetchTrack(trackId: String) {
        if (trackId.isBlank()) return
        // A local cache already has its authorized descriptor. Prefetching it must never renew
        // credentials or issue a network manifest request.
        if (isMarkedDownloaded(trackId)) {
            Log.d(DIRECT_LOG_TAG, "manifest prefetch skipped for local track id=$trackId")
            return
        }
        val generation = queueGeneration
        val request = manifestRequest(trackId, generation)
        scope.launch {
            request.deferred.await().onSuccess { manifest ->
                Log.d(DIRECT_LOG_TAG, "manifest prefetched id=$trackId source=${manifest.source} presentation=${manifest.presentation} mime=${manifest.mimeType} codec=${manifest.codec.orEmpty()}")
            }.onFailure { error ->
                manifestRequests.remove(trackId, request)
                if (generation == queueGeneration) {
                    Log.w(DIRECT_LOG_TAG, "manifest prefetch failed id=$trackId reason=${error::class.java.simpleName}: ${error.message}")
                }
            }
        }
    }

    private suspend fun resolveMediaSource(
        track: TidalTrack,
        queue: List<TidalTrack> = listOf(track),
        queueIndex: Int = 0,
        generation: Long = queueGeneration,
    ): ResolvedMediaSource {
        val startedAt = SystemClock.elapsedRealtime()
        val trackId = track.id
        val local = localOfflineSource(trackId)
        val manifest = if (local != null) {
            local.descriptor.toResolvedManifest()
        } else {
            val request = manifestRequest(trackId, generation)
            request.deferred.await().getOrThrow().also { manifestRequests.remove(trackId, request) }
        }
        val manifestMs = SystemClock.elapsedRealtime() - startedAt
        Log.d(DIRECT_LOG_TAG, "manifest loaded id=$trackId queueIndex=$queueIndex manifestMs=$manifestMs source=${manifest.source} presentation=${manifest.presentation} previewReason=${manifest.previewReason.orEmpty()} mime=${manifest.mimeType} codec=${manifest.codec.orEmpty()}")
        val source = when (manifest.kind) {
            ManifestKind.Dash -> {
                val mediaSourceFactory = if (local != null) {
                    Log.d(DIRECT_LOG_TAG, "using offline cache for downloaded track id=$trackId wakeMode=LOCAL")
                    DashMediaSource.Factory(
                        CacheDataSource.Factory()
                            .setCache(local.cache)
                            .setCacheKeyFactory(canonicalDownloadCacheKeyFactory(trackId))
                            // A cache miss is corruption/incompletion, never a reason to contact
                            // the manifest host or stream a different entitlement/quality.
                            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
                            .setUpstreamDataSourceFactory(null),
                    )
                } else {
                    DashMediaSource.Factory(DefaultDataSource.Factory(appContext))
                }
                mediaSourceFactory.createMediaSource(manifest.mediaItem(track, queue, queueIndex))
            }
            ManifestKind.DirectUrl -> ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext))
                .createMediaSource(manifest.mediaItem(track, queue, queueIndex))
        }
        return ResolvedMediaSource(manifest = manifest, source = source, offline = local != null)
    }

    private fun manifestRequest(trackId: String, generation: Long): ManifestRequest =
        manifestRequests.compute(trackId) { id, existing ->
            if (existing != null && existing.generation == generation && !existing.deferred.isCancelled) {
                existing
            } else {
                existing?.deferred?.cancel()
                cancelManifestCall(id)
                ManifestRequest(
                    generation = generation,
                    deferred = scope.async(Dispatchers.IO) { runCatching { fetchPlaybackManifest(id) } },
                )
            }
        } ?: error("manifest request unexpectedly missing")

    private fun cancelManifestRequestsExcept(keepGeneration: Long) {
        manifestRequests.entries.forEach { (trackId, request) ->
            if (shouldCancelManifestRequest(request.generation, keepGeneration) && manifestRequests.remove(trackId, request)) {
                request.deferred.cancel()
                cancelManifestCall(trackId)
            }
        }
    }

    private fun cancelManifestRequests() {
        manifestRequests.entries.forEach { (trackId, request) ->
            if (manifestRequests.remove(trackId, request)) request.deferred.cancel()
            cancelManifestCall(trackId)
        }
    }

    private fun cancelManifestCall(trackId: String) {
        activeManifestCalls.remove(trackId)?.cancel()
    }

    override fun play() {
        player.playWhenReady = true
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seek(positionSeconds: Float) {
        player.seekTo((positionSeconds * 1000f).toLong().coerceAtLeast(0L))
    }

    override fun release() {
        watchdogHandler.removeCallbacks(watchdogTick)
        lookAheadJob?.cancel()
        cancelManifestRequests()
        player.setWakeMode(C.WAKE_MODE_NONE)
        player.release()
        offlineCaches.keys.forEach { id -> runCatching { OfflineCacheRegistry.release(appContext, id) } }
        offlineCaches.clear()
    }

    private fun downloadedTrackCache(trackId: String): SimpleCache? {
        if (!appContext.isOfflineTrackDownloaded(trackId)) return null
        val cacheDir = appContext.offlineTrackCacheDir(trackId)
        if (!cacheDir.isDirectory) return null
        val cache = offlineCaches.computeIfAbsent(trackId) {
            OfflineCacheRegistry.acquire(appContext, trackId)
        }
        return cache.takeIf { it.cacheSpace > 0L && it.keys.isNotEmpty() }
    }

    private fun localOfflineSource(trackId: String): LocalOfflineSource? {
        val downloadedMarker = isMarkedDownloaded(trackId)
        if (!downloadedMarker) return null
        val descriptor = appContext.offlinePlaybackDescriptor(trackId)
        val cache = descriptor?.let { downloadedTrackCache(trackId) }
        return when (offlinePlaybackPlan(downloadedMarker, descriptor != null, cache != null)) {
            OfflinePlaybackPlan.Local -> LocalOfflineSource(checkNotNull(descriptor), checkNotNull(cache))
            OfflinePlaybackPlan.RepairDescriptor ->
                throw IOException("Downloaded track needs repair: authorized offline descriptor is missing")
            OfflinePlaybackPlan.RepairCache ->
                throw IOException("Downloaded track needs repair: local cache is incomplete or corrupt").also {
                    appContext.markOfflineTrackRepairRequired(trackId)
                }
            OfflinePlaybackPlan.Stream -> null
        }
    }

    private fun canonicalDownloadCacheKeyFactory(trackId: String): CacheKeyFactory = CacheKeyFactory { dataSpec: DataSpec ->
        dataSpec.key ?: "untidy-download-$trackId-${sha256Short(dataSpec.uri.toString())}"
    }

    private fun isMarkedDownloaded(trackId: String): Boolean = appContext.isOfflineTrackDownloaded(trackId)

    private fun fetchPlaybackManifest(trackId: String): ResolvedManifest {
        val startedAt = SystemClock.elapsedRealtime()
        val tokenStartedAt = SystemClock.elapsedRealtime()
        val token = kotlinx.coroutines.runBlocking { authRepository.getAccessToken() }
            ?: throw IOException("No TIDAL access token")
        val clientId = kotlinx.coroutines.runBlocking { authRepository.getClientIdForApi() }
        Log.d(DIRECT_LOG_TAG, "manifest auth ready id=$trackId authMs=${SystemClock.elapsedRealtime() - tokenStartedAt} totalMs=${SystemClock.elapsedRealtime() - startedAt}")
        if (isMarkedDownloaded(trackId)) {
            runCatching { fetchTrackManifest(trackId, token, clientId, usage = "DOWNLOAD") }
                .onFailure { Log.w(DIRECT_LOG_TAG, "download manifest failed id=$trackId reason=${it::class.java.simpleName}: ${it.message}") }
                .getOrNull()
                ?.let { return it }
        }
        val desktopStartedAt = SystemClock.elapsedRealtime()
        runCatching { fetchDesktopPlaybackInfo(trackId, token, clientId) }
            .onSuccess { Log.d(DIRECT_LOG_TAG, "desktop playbackinfo ok id=$trackId elapsedMs=${SystemClock.elapsedRealtime() - desktopStartedAt} totalMs=${SystemClock.elapsedRealtime() - startedAt}") }
            .onFailure { Log.w(DIRECT_LOG_TAG, "desktop playbackinfo failed id=$trackId elapsedMs=${SystemClock.elapsedRealtime() - desktopStartedAt} reason=${it::class.java.simpleName}: ${it.message}") }
            .getOrNull()
            ?.let { return it }

        val openApiStartedAt = SystemClock.elapsedRealtime()
        return fetchTrackManifest(trackId, token, clientId, usage = "PLAYBACK")
            .also { Log.d(DIRECT_LOG_TAG, "openapi playback manifest ok id=$trackId elapsedMs=${SystemClock.elapsedRealtime() - openApiStartedAt} totalMs=${SystemClock.elapsedRealtime() - startedAt}") }
    }

    private fun fetchTrackManifest(trackId: String, token: String, clientId: String, usage: String): ResolvedManifest {
        var lastFailure: IOException? = null
        manifestFormatAttempts(audioPreset).forEach { formats ->
            try {
                return fetchTrackManifestAttempt(trackId, token, clientId, usage, formats)
            } catch (error: IOException) {
                // The API can reject a format that is unavailable for this account/track. Keep
                // trying the ordered list so FullFidelity still reaches the AAC fallback.
                lastFailure = error
                Log.w(
                    DIRECT_LOG_TAG,
                    "track manifest format attempt failed id=$trackId usage=$usage formats=${formats.joinToString(",")} reason=${error.message}",
                )
            }
        }
        throw lastFailure ?: IOException("track manifest request had no format candidates")
    }

    private fun fetchTrackManifestAttempt(
        trackId: String,
        token: String,
        clientId: String,
        usage: String,
        formats: List<String>,
    ): ResolvedManifest {
        val urlBuilder = "https://openapi.tidal.com/v2/trackManifests/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("manifestType", "MPEG_DASH")
            .addQueryParameter("uriScheme", "DATA")
            .addQueryParameter("usage", usage)
            .addQueryParameter("adaptive", "false")
        formats.forEach { urlBuilder.addQueryParameter("formats", it) }
        val url = urlBuilder.build()
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/vnd.api+json")
            .header("X-Tidal-Token", clientId)
            .header("Authorization", "Bearer $token")
            .build()
        return executeManifestCall(trackId, request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("track manifest ${response.code}: ${body.take(240)}")
            }
            body.toTrackManifest(trackId, usage)
        }
    }

    private fun fetchDesktopPlaybackInfo(trackId: String, token: String, clientId: String): ResolvedManifest {
        val url = "https://api.tidalhifi.com/v1/tracks/$trackId/playbackinfopostpaywall".toHttpUrl().newBuilder()
            .addQueryParameter("audioquality", legacyAudioQuality())
            .addQueryParameter("playbackmode", "STREAM")
            .addQueryParameter("assetpresentation", "FULL")
            .addQueryParameter("countryCode", countryCode())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        return executeManifestCall(trackId, request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("desktop playbackinfo ${response.code}: ${body.take(240)}")
            }
            body.toDesktopPlaybackManifest(trackId)
        }
    }

    private fun legacyAudioQuality(): String = legacyAudioQualityForPreset(audioPreset)

    private fun countryCode(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    private fun <T> executeManifestCall(trackId: String, request: Request, block: (okhttp3.Response) -> T): T {
        val call = httpClient.newCall(request)
        activeManifestCalls.put(trackId, call)?.cancel()
        return try {
            call.execute().use(block)
        } finally {
            activeManifestCalls.remove(trackId, call)
        }
    }
}

/**
 * Ordered OpenAPI format attempts for each preset. The generated TIDAL API artifact verifies these
 * format tokens: FLAC_HIRES, FLAC, HEAACV1, and AACLC. FullFidelity intentionally makes the
 * lossless requests separately so an unavailable hi-res/lossless tier cannot prevent AAC fallback.
 */
internal fun manifestFormatAttempts(preset: AudioPreset): List<List<String>> = when (preset) {
    AudioPreset.BatterySaver -> listOf(listOf("HEAACV1"))
    AudioPreset.Balanced,
    AudioPreset.High,
    -> listOf(listOf("HEAACV1", "AACLC"))
    AudioPreset.FullFidelity -> listOf(
        listOf("FLAC_HIRES"),
        listOf("FLAC"),
        listOf("HEAACV1", "AACLC"),
    )
}

/** Legacy playbackinfo uses the same quality names exposed by the SDK's AudioQuality enum. */
internal fun legacyAudioQualityForPreset(preset: AudioPreset): String = when (preset) {
    AudioPreset.BatterySaver -> "LOW"
    AudioPreset.Balanced,
    AudioPreset.High,
    -> "HIGH"
    AudioPreset.FullFidelity -> "HI_RES_LOSSLESS"
}

private enum class ManifestKind {
    Dash,
    DirectUrl,
}

private data class ResolvedManifest(
    val trackId: String,
    val uri: String,
    val kind: ManifestKind,
    val source: String,
    val presentation: String,
    val previewReason: String?,
    val mimeType: String,
    val codec: String?,
) {
    companion object {
        fun placeholder(trackId: String): ResolvedManifest = ResolvedManifest(
            trackId = trackId,
            uri = "",
            kind = ManifestKind.DirectUrl,
            source = "exo-playlist-transition",
            presentation = "UNKNOWN",
            previewReason = null,
            mimeType = "",
            codec = null,
        )
    }
}

private data class ResolvedMediaSource(
    val manifest: ResolvedManifest,
    val source: androidx.media3.exoplayer.source.MediaSource,
    val offline: Boolean,
)

private data class LocalOfflineSource(
    val descriptor: OfflinePlaybackDescriptor,
    val cache: SimpleCache,
)

private data class ManifestRequest(
    val generation: Long,
    val deferred: Deferred<Result<ResolvedManifest>>,
)

private fun ResolvedManifest.mediaItem(
    track: TidalTrack,
    queue: List<TidalTrack>,
    queueIndex: Int,
): MediaItem = MediaItem.Builder()
    .setMediaId(track.id)
    .setUri(Uri.parse(uri))
    .setMimeType(if (kind == ManifestKind.Dash) MimeTypes.APPLICATION_MPD else mimeType.takeIf { it.isNotBlank() })
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setDurationMs(track.durationMs.takeIf { it > 0 })
            .setExtras(
                Bundle().apply {
                    putString(PlaybackActions.EXTRA_ALBUM_ID, track.albumId)
                    putString(PlaybackActions.EXTRA_ARTIST_ID, track.artistId)
                    if (queue.isNotEmpty()) {
                        putString(PlaybackActions.EXTRA_QUEUE_PAYLOAD, PlaybackQueueStore.payloadFor(queue))
                        putInt(PlaybackActions.EXTRA_QUEUE_INDEX, queueIndex.coerceIn(0, queue.lastIndex))
                    }
                },
            )
            .build(),
    )
    .build()

private fun String.toTrackManifest(trackId: String, usage: String = "PLAYBACK"): ResolvedManifest {
    val attributes = JSONObject(this)
        .getJSONObject("data")
        .optJSONObject("attributes")
        ?: throw IOException("Track manifest missing attributes")
    val uri = attributes.optString("uri").takeIf { it.isNotBlank() }
        ?: throw IOException("Track manifest missing uri")
    val formats = attributes.optJSONArray("formats").toStringList()
    return ResolvedManifest(
        trackId = trackId,
        uri = uri,
        kind = ManifestKind.Dash,
        source = "openapi-trackManifests-$usage",
        presentation = attributes.optString("trackPresentation", "UNKNOWN"),
        previewReason = attributes.optString("previewReason").takeIf { it.isNotBlank() },
        mimeType = MimeTypes.APPLICATION_MPD,
        codec = formats.lastOrNull(),
    )
}

private fun String.toDesktopPlaybackManifest(trackId: String): ResolvedManifest {
    val root = JSONObject(this)
    val manifestMimeType = root.optString("manifestMimeType")
    val encodedManifest = root.optString("manifest").takeIf { it.isNotBlank() }
        ?: throw IOException("Desktop playbackinfo missing manifest")
    val presentation = root.optString("assetPresentation", root.optString("trackPresentation", "UNKNOWN"))
    val previewReason = root.optString("previewReason").takeIf { it.isNotBlank() }
    return when (manifestMimeType) {
        "application/vnd.tidal.bts" -> {
            val bts = JSONObject(String(Base64.decode(encodedManifest, Base64.DEFAULT), Charsets.UTF_8))
            val urls = bts.optJSONArray("urls").toStringList()
            val url = urls.firstOrNull() ?: throw IOException("BTS manifest missing urls")
            val encryptionType = bts.optString("encryptionType").takeIf { it.isNotBlank() }
            if (encryptionType != null && encryptionType != "NONE") {
                throw IOException("Unsupported BTS encryptionType=$encryptionType")
            }
            ResolvedManifest(
                trackId = trackId,
                uri = url,
                kind = ManifestKind.DirectUrl,
                source = "desktop-playbackinfo-bts",
                presentation = presentation,
                previewReason = previewReason,
                mimeType = bts.optString("mimeType", "audio/mp4"),
                codec = bts.optString("codecs").takeIf { it.isNotBlank() },
            )
        }
        "application/dash+xml" -> ResolvedManifest(
            trackId = trackId,
            uri = "data:application/dash+xml;base64,$encodedManifest",
            kind = ManifestKind.Dash,
            source = "desktop-playbackinfo-dash",
            presentation = presentation,
            previewReason = previewReason,
            mimeType = MimeTypes.APPLICATION_MPD,
            codec = null,
        )
        else -> throw IOException("Unsupported manifestMimeType=$manifestMimeType")
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
}

private fun ResolvedManifest.toDiagnosticContext(playerDurationMs: Long, queueIndex: Int? = null): PlaybackDiagnosticContext = PlaybackDiagnosticContext(
    productId = trackId,
    queueIndex = queueIndex,
    assetPresentation = presentation,
    durationSeconds = playerDurationMs.takeIf { it > 0L }?.let { it / 1000f } ?: 0f,
    previewReason = previewReason,
    // Manifest source identifies how the asset was resolved, not the quality actually selected
    // by ExoPlayer. Delivered audio details are emitted from onTracksChanged instead.
    audioQuality = null,
    audioCodec = codec,
)

private fun Player.getMediaItemAtOrNull(index: Int): MediaItem? =
    if (index in 0 until mediaItemCount) getMediaItemAt(index) else null

private fun Int.toBackendState(isPlaying: Boolean): PlaybackBackendState = when (this) {
    Player.STATE_BUFFERING -> PlaybackBackendState.Stalled
    Player.STATE_READY -> if (isPlaying) PlaybackBackendState.Playing else PlaybackBackendState.NotPlaying
    Player.STATE_IDLE -> PlaybackBackendState.Idle
    Player.STATE_ENDED -> PlaybackBackendState.NotPlaying
    else -> PlaybackBackendState.NotPlaying
}

private fun OfflinePlaybackDescriptor.toResolvedManifest(): ResolvedManifest = ResolvedManifest(
    trackId = trackId,
    uri = manifestUri,
    kind = ManifestKind.Dash,
    source = "offline-download-descriptor",
    presentation = presentation,
    previewReason = previewReason,
    mimeType = MimeTypes.APPLICATION_MPD,
    codec = format,
)

private fun Int.isSelectableWearOutputType(): Boolean = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    -> true
    else -> false
}

private fun sha256Short(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
    .take(12)

private const val DIRECT_LOG_TAG = "Untidy/DirectPlayback"
private const val OFFLOAD_WATCHDOG_INTERVAL_MS = 2000L

/** Rebuild attempts per media item before holding; guards against pathological storms. */
private const val MAX_REBUILDS_PER_ITEM = 2
