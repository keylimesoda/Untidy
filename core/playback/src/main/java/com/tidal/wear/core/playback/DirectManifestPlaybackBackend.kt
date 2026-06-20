@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
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
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.core.playback.offline.offlineTrackCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
    private val databaseProvider = StandaloneDatabaseProvider(appContext)
    private val offlineCaches = ConcurrentHashMap<String, SimpleCache>()
    private var audioPreset = AudioPreset.BatterySaver
    private var currentManifest: ResolvedManifest? = null
    private val manifestRequests = ConcurrentHashMap<String, Deferred<Result<ResolvedManifest>>>()
    private val player = ExoPlayer.Builder(appContext)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
        .also { exo ->
            exo.setWakeMode(C.WAKE_MODE_NETWORK)
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        currentManifest?.let { _events.tryEmit(PlaybackBackendEvent.MediaEnded(it.toDiagnosticContext(exo.duration))) }
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
                    val mediaId = mediaItem?.mediaId.orEmpty()
                    currentManifest = mediaId.takeIf { it.isNotBlank() }
                        ?.let { id -> currentManifest?.takeIf { it.trackId == id } ?: ResolvedManifest.placeholder(id) }
                    currentManifest?.let { manifest ->
                        _events.tryEmit(PlaybackBackendEvent.MediaTransition(manifest.toDiagnosticContext(exo.duration)))
                    }
                    mediaId.takeIf { it.isNotBlank() }?.let { id ->
                        exo.setWakeMode(if (appContext.isOfflineTrackDownloaded(id)) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
                    }
                    val nextIndex = exo.currentMediaItemIndex + 1
                    exo.getMediaItemAtOrNull(nextIndex)?.mediaId?.takeIf { it.isNotBlank() }?.let(::prefetchTrack)
                }

                override fun onPlayerError(error: PlaybackException) {
                    _events.tryEmit(PlaybackBackendEvent.Error(error.errorCodeName, error))
                }
            },
        )
    }

    override val events: Flow<PlaybackBackendEvent> = _events.asSharedFlow()
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    override val sessionPlayer: Player get() = player

    override fun setAudioPreset(preset: AudioPreset) {
        if (audioPreset != preset) {
            manifestRequests.clear()
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
        val playableTracks = tracks.filter { it.id.isNotBlank() }
        if (playableTracks.isEmpty()) return
        val boundedStartIndex = startIndex.coerceIn(0, playableTracks.lastIndex)
        runCatching {
            Log.d(DIRECT_LOG_TAG, "loadQueue start size=${playableTracks.size} start=$boundedStartIndex tail=${playableTracks.size - boundedStartIndex}")
            val resolvedQueue = playableTracks
                .drop(boundedStartIndex)
                .mapIndexed { offset, track -> resolveMediaSource(track, playableTracks, boundedStartIndex + offset) }
            val first = resolvedQueue.first()
            currentManifest = first.manifest
            player.setWakeMode(if (first.offline) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
            player.setMediaSources(resolvedQueue.map { it.source }, 0, C.TIME_UNSET)
            player.prepare()
            Log.d(DIRECT_LOG_TAG, "loadQueue prepared size=${resolvedQueue.size} elapsedMs=${SystemClock.elapsedRealtime() - queueStartedAt}")
            _events.emit(PlaybackBackendEvent.MediaTransition(first.manifest.toDiagnosticContext(player.duration)))
            _events.emit(PlaybackBackendEvent.QualityChanged(first.manifest.toDiagnosticContext(player.duration)))
            playableTracks.getOrNull(boundedStartIndex + 1)?.id?.let(::prefetchTrack)
        }.onFailure { error ->
            playableTracks.forEach { manifestRequests.remove(it.id) }
            Log.e(DIRECT_LOG_TAG, "manifest queue load failed start=${playableTracks.getOrNull(boundedStartIndex)?.id.orEmpty()}", error)
            _events.emit(PlaybackBackendEvent.Error(error::class.java.simpleName, error))
        }.getOrThrow()
    }

    override fun prefetchTrack(trackId: String) {
        if (trackId.isBlank()) return
        val request = manifestRequest(trackId)
        scope.launch {
            request.await().onSuccess { manifest ->
                Log.d(DIRECT_LOG_TAG, "manifest prefetched id=$trackId source=${manifest.source} presentation=${manifest.presentation} mime=${manifest.mimeType} codec=${manifest.codec.orEmpty()}")
            }.onFailure { error ->
                manifestRequests.remove(trackId)
                Log.w(DIRECT_LOG_TAG, "manifest prefetch failed id=$trackId reason=${error::class.java.simpleName}: ${error.message}")
            }
        }
    }

    private suspend fun resolveMediaSource(
        track: TidalTrack,
        queue: List<TidalTrack> = listOf(track),
        queueIndex: Int = 0,
    ): ResolvedMediaSource {
        val startedAt = SystemClock.elapsedRealtime()
        val trackId = track.id
        val manifest = manifestRequest(trackId).await().getOrThrow()
        val manifestMs = SystemClock.elapsedRealtime() - startedAt
        manifestRequests.remove(trackId)
        Log.d(DIRECT_LOG_TAG, "manifest loaded id=$trackId queueIndex=$queueIndex manifestMs=$manifestMs source=${manifest.source} presentation=${manifest.presentation} previewReason=${manifest.previewReason.orEmpty()} mime=${manifest.mimeType} codec=${manifest.codec.orEmpty()}")
        val offlineCache = downloadedTrackCache(trackId)
        val source = when (manifest.kind) {
            ManifestKind.Dash -> {
                val mediaSourceFactory = if (offlineCache != null) {
                    Log.d(DIRECT_LOG_TAG, "using offline cache for downloaded track id=$trackId wakeMode=LOCAL")
                    DashMediaSource.Factory(
                        CacheDataSource.Factory()
                            .setCache(offlineCache)
                            .setCacheKeyFactory(canonicalDownloadCacheKeyFactory(trackId))
                            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext)),
                    )
                } else {
                    DashMediaSource.Factory(DefaultDataSource.Factory(appContext))
                }
                mediaSourceFactory.createMediaSource(manifest.mediaItem(track, queue, queueIndex))
            }
            ManifestKind.DirectUrl -> ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext))
                .createMediaSource(manifest.mediaItem(track, queue, queueIndex))
        }
        return ResolvedMediaSource(manifest = manifest, source = source, offline = offlineCache != null)
    }

    private fun manifestRequest(trackId: String): Deferred<Result<ResolvedManifest>> =
        manifestRequests.computeIfAbsent(trackId) { id ->
            scope.async(Dispatchers.IO) { runCatching { fetchPlaybackManifest(id) } }
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
        player.setWakeMode(C.WAKE_MODE_NONE)
        player.release()
        offlineCaches.values.forEach { cache -> runCatching { cache.release() } }
        offlineCaches.clear()
    }

    private fun downloadedTrackCache(trackId: String): SimpleCache? {
        if (!appContext.isOfflineTrackDownloaded(trackId)) return null
        val cacheDir = appContext.offlineTrackCacheDir(trackId)
        if (!cacheDir.isDirectory) return null
        return offlineCaches.computeIfAbsent(trackId) {
            SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        }
    }

    private fun canonicalDownloadCacheKeyFactory(trackId: String): CacheKeyFactory = CacheKeyFactory { dataSpec: DataSpec ->
        dataSpec.key ?: "untidy-download-proof-$trackId-${sha256Short(dataSpec.uri.toString())}"
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
        val urlBuilder = "https://openapi.tidal.com/v2/trackManifests/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("manifestType", "MPEG_DASH")
            .addQueryParameter("uriScheme", "DATA")
            .addQueryParameter("usage", usage)
            .addQueryParameter("adaptive", "false")
        requestedFormats().forEach { urlBuilder.addQueryParameter("formats", it) }
        val url = urlBuilder.build()
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/vnd.api+json")
            .header("X-Tidal-Token", clientId)
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("track manifest ${response.code}: ${body.take(240)}")
            }
            return body.toTrackManifest(trackId, usage)
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
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("desktop playbackinfo ${response.code}: ${body.take(240)}")
            }
            return body.toDesktopPlaybackManifest(trackId)
        }
    }

    private fun requestedFormats(): List<String> = when (audioPreset) {
        AudioPreset.BatterySaver -> listOf("HEAACV1")
        AudioPreset.Balanced,
        AudioPreset.High,
        -> listOf("HEAACV1", "AACLC")
    }

    private fun legacyAudioQuality(): String = when (audioPreset) {
        AudioPreset.BatterySaver -> "LOW"
        AudioPreset.Balanced,
        AudioPreset.High,
        -> "HIGH"
    }

    private fun countryCode(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
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

private fun ResolvedManifest.toDiagnosticContext(playerDurationMs: Long): PlaybackDiagnosticContext = PlaybackDiagnosticContext(
    productId = trackId,
    assetPresentation = presentation,
    durationSeconds = playerDurationMs.takeIf { it > 0L }?.let { it / 1000f } ?: 0f,
    previewReason = previewReason,
    audioQuality = source,
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

private fun sha256Short(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
    .take(12)

private const val DIRECT_LOG_TAG = "Untidy/DirectPlayback"
