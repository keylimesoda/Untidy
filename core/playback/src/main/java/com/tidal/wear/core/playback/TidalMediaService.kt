@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.core.playback.settings.sharedSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PLAYER_LOG_TAG = "Untidy/Player"

class TidalMediaService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var playbackBackend: PlaybackBackend? = null
    private var sessionPlayer: Player? = null
    private var session: MediaLibrarySession? = null
    private var currentTrack: TidalTrack? = null
    private var currentQueue: List<TidalTrack> = emptyList()
    private var currentQueueIndex: Int = 0
    private var currentTrackStartedAtMs: Long = 0L
    private lateinit var apiClient: TidalApiClient
    private lateinit var authRepository: com.tidal.wear.core.auth.TidalAuthRepository

    override fun onCreate() {
        super.onCreate()
        authRepository = TidalAuthRepositoryProvider.get(this)
        apiClient = TidalApiClient(authRepository)
        val backend = DirectManifestPlaybackBackend(
            context = this,
            scope = serviceScope,
            authRepository = authRepository,
        )
        playbackBackend = backend
        val player = backend.sessionPlayer ?: error("DirectManifestPlaybackBackend must expose the MediaSession player")
        sessionPlayer = player
        serviceScope.launch {
            backend.events.collect { event ->
                Log.d(PLAYER_LOG_TAG, "backend event: ${event::class.simpleName.orEmpty()}")
                when (event) {
                    is PlaybackBackendEvent.StateChanged -> Log.d(PLAYER_LOG_TAG, "backend state: ${event.state}")
                    is PlaybackBackendEvent.MediaTransition -> logPlaybackContext("transition", event.context)
                    is PlaybackBackendEvent.MediaEnded -> logPlaybackContext("ended", event.context)
                    is PlaybackBackendEvent.QualityChanged -> logPlaybackContext("quality", event.context)
                    is PlaybackBackendEvent.Error -> {
                        Log.e(PLAYER_LOG_TAG, "backend playback error event: ${event.code}", event.throwable)
                        player.pause()
                    }
                    is PlaybackBackendEvent.Other -> if (event.name.contains("Error", ignoreCase = true)) {
                        Log.e(PLAYER_LOG_TAG, "backend playback error event: ${event.name}")
                    }
                }
            }
        }
        session = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    if (!isAllowedController(session, controllerInfo)) {
                        Log.w(
                            PLAYER_LOG_TAG,
                            "rejecting media controller package=${controllerInfo.packageName} uid=${controllerInfo.uid} trusted=${controllerInfo.isTrusted}",
                        )
                        return MediaSession.ConnectionResult.reject()
                    }
                    return super.onConnect(session, controllerInfo)
                }
            },
        )
            .setSessionActivity(playerActivityPendingIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    private fun isAllowedController(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
    ): Boolean = MediaControllerAccessPolicy.isAllowedController(
        ownPackageName = packageName,
        controllerPackageName = controllerInfo.packageName,
        isTrusted = controllerInfo.isTrusted,
        isMediaNotificationController = session.isMediaNotificationController(controllerInfo),
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        Log.d(PLAYER_LOG_TAG, "onStartCommand action=${action ?: "null"}")

        // This service is intentionally still exported while MediaLibraryService controller support
        // is validated. Media3 controllers are filtered in onConnect(); explicit custom service
        // actions must also be app-authored so another package cannot start authenticated playback,
        // auth probes, queue jumps, or transport controls via startForegroundService().
        if (PlaybackServiceForegroundPolicy.requiresAppCommandToken(action) &&
            !PlaybackCommandTokenProvider.isValid(this, intent)
        ) {
            Log.w(PLAYER_LOG_TAG, "rejecting service action without app command token action=${action ?: "null"}")
            stopSelfResult(startId)
            return result
        }

        // If an app-authored idle/no-op control intent starts us as a foreground service, Android
        // still requires us to promote to foreground quickly or stop this start request.
        if (PlaybackServiceForegroundPolicy.shouldStopStartedServiceWhenIdle(action) && currentTrack == null) {
            Log.d(PLAYER_LOG_TAG, "idle control action ignored; stopping foreground-service startId=$startId")
            stopSelfResult(startId)
            return result
        }

        when (action) {
            PlaybackActions.ACTION_PLAY_FIXTURE -> {
                playTrack("fixture-run-01", fixtureTrack())
            }
            PlaybackActions.ACTION_PROBE_DEVICE_AUTH -> probeDeviceAuth()
            PlaybackActions.ACTION_PLAY_TRACK -> {
                val trackId = intent.getStringExtra(PlaybackActions.EXTRA_TRACK_ID).orEmpty()
                if (trackId.isBlank()) {
                    Log.w(PLAYER_LOG_TAG, "playTrack ignored blank track id; stopping foreground-service startId=$startId")
                    stopSelfResult(startId)
                    return result
                }
                if (!canStartLiveOrDownloadedPlayback(trackId)) {
                    Log.w(PLAYER_LOG_TAG, "playTrack offline fallback: not downloaded id=$trackId; stopping foreground-service startId=$startId")
                    val knownTrack = intent.toTrackOrNull()
                    Log.w(PLAYER_LOG_TAG, "Not downloaded · connect to stream this track")
                    stopSelfResult(startId)
                    return result
                }
                val knownTrack = intent.toTrackOrNull()
                currentQueue = emptyList()
                currentQueueIndex = 0
                playTrack(trackId = trackId, knownTrack = knownTrack)
            }
            PlaybackActions.ACTION_PLAY_QUEUE -> {
                val queueStarted = playQueue(
                    queueId = intent.getStringExtra(PlaybackActions.EXTRA_QUEUE_ID).orEmpty(),
                    queuePayload = intent.getStringExtra(PlaybackActions.EXTRA_QUEUE_PAYLOAD).orEmpty(),
                    startIndex = intent.getIntExtra(PlaybackActions.EXTRA_QUEUE_START_INDEX, 0),
                )
                if (!queueStarted) {
                    stopSelfResult(startId)
                }
            }
            PlaybackActions.ACTION_PAUSE -> sessionPlayer?.pause()
            PlaybackActions.ACTION_RESUME -> sessionPlayer?.play()
            PlaybackActions.ACTION_SKIP_NEXT -> skipToNextInQueue()
            PlaybackActions.ACTION_SKIP_PREVIOUS -> skipToPreviousInQueue()
            PlaybackActions.ACTION_JUMP_TO_QUEUE_INDEX -> jumpToQueueIndex(
                intent.getIntExtra(PlaybackActions.EXTRA_QUEUE_INDEX, -1),
            )
        }
        return result
    }

    private fun probeDeviceAuth() {
        serviceScope.launch {
            runCatching {
                val session = authRepository.startDeviceAuth()
                Log.d(
                    "Untidy/Auth",
                    "probe device auth success: verification_uri=${session.verificationUri}, verification_uri_complete=${session.verificationUriComplete.orEmpty()}, user_code=${session.userCode}, expires_in=${session.expiresInSeconds}, interval=${session.intervalSeconds}",
                )
            }.onFailure {
                Log.e("Untidy/Auth", "startDeviceAuth FAILED", it)
            }
        }
    }

    private fun playTrack(trackId: String, knownTrack: TidalTrack? = null) {
        val id = trackId.ifBlank { return }
        if (!canStartLiveOrDownloadedPlayback(id)) {
            Log.w(PLAYER_LOG_TAG, "playTrack offline fallback: not downloaded id=$id")
            Log.w(PLAYER_LOG_TAG, "Not downloaded · connect to stream this track")
            return
        }
        Log.d(PLAYER_LOG_TAG, "playTrack entry id=$id knownTrack.title=${knownTrack?.title.orEmpty()}")
        serviceScope.launch {
            configureQuality()
            val track = knownTrack ?: withContext(Dispatchers.IO) { runCatching { apiClient.track(id) }.getOrNull() }
                ?: TidalTrack(id = id, title = "TIDAL", artist = "", album = "")
            Log.d(
                PLAYER_LOG_TAG,
                "resolved track id=${track.id} title=${track.title} artist=${track.artist} durationMs=${track.durationMs}",
            )
            currentTrack = track
            currentTrackStartedAtMs = SystemClock.elapsedRealtime()
            prefetchNextInQueue()
            runCatching {
                Log.d(PLAYER_LOG_TAG, "backend load start id=$id")
                if (currentQueue.isNotEmpty()) {
                    playbackBackend?.loadQueue(currentQueue, currentQueueIndex)
                } else {
                    playbackBackend?.loadTrack(track)
                }
                Log.d(PLAYER_LOG_TAG, "backend load completed id=$id")
                Log.d(PLAYER_LOG_TAG, "backend play start")
                playbackBackend?.play()
                Log.d(PLAYER_LOG_TAG, "backend play completed")
            }.onFailure {
                Log.e(PLAYER_LOG_TAG, "backend load/play failed", it)
                Log.e(PLAYER_LOG_TAG, "Playback failed: ${it.message ?: it::class.java.simpleName}")
            }
        }
    }

    private fun playQueue(queueId: String, queuePayload: String, startIndex: Int): Boolean {
        val queue = PlaybackQueueStore.get(queueId).ifEmpty { PlaybackQueueStore.fromPayload(queuePayload) }
        if (queue.isEmpty()) {
            Log.w(PLAYER_LOG_TAG, "playQueue ignored empty queue id=$queueId payload=${queuePayload.isNotBlank()}")
            return false
        }
        val playableQueue = if (isNetworkAvailable()) {
            queue
        } else {
            queue.filter { isOfflineTrackDownloaded(it.id) }
        }
        if (playableQueue.isEmpty()) {
            Log.w(PLAYER_LOG_TAG, "playQueue offline fallback: no downloaded tracks queueId=$queueId")
            Log.w(PLAYER_LOG_TAG, "Not downloaded · connect to stream this track")
            return false
        }
        val requestedTrackId = queue.getOrNull(startIndex.coerceIn(0, queue.lastIndex))?.id
        currentQueue = playableQueue
        currentQueueIndex = requestedTrackId
            ?.let { id -> playableQueue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val track = playableQueue[currentQueueIndex]
        Log.d(PLAYER_LOG_TAG, "playQueue id=$queueId size=${queue.size} start=$currentQueueIndex track=${track.id}")
        playTrack(track.id, track)
        return true
    }


    private fun canStartLiveOrDownloadedPlayback(trackId: String): Boolean =
        isNetworkAvailable() || isOfflineTrackDownloaded(trackId)

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun skipToNextInQueue() {
        val next = currentQueueIndex + 1
        if (hasNextInQueue()) {
            currentQueueIndex = next
            val track = currentQueue[currentQueueIndex]
            Log.d(PLAYER_LOG_TAG, "queue next index=$currentQueueIndex id=${track.id}")
            sessionPlayer?.seekToNextMediaItem()
        } else {
            Log.d(PLAYER_LOG_TAG, "queue next unavailable size=${currentQueue.size} index=$currentQueueIndex")
        }
    }

    private fun hasNextInQueue(): Boolean = currentQueue.isNotEmpty() && currentQueueIndex + 1 <= currentQueue.lastIndex

    private fun prefetchNextInQueue() {
        val nextTrackId = currentQueue.getOrNull(currentQueueIndex + 1)?.id?.takeIf { it.isNotBlank() } ?: return
        Log.d(PLAYER_LOG_TAG, "queue prefetch next index=${currentQueueIndex + 1} id=$nextTrackId")
        playbackBackend?.prefetchTrack(nextTrackId)
    }

    private fun skipToPreviousInQueue() {
        val previous = currentQueueIndex - 1
        if (currentQueue.isNotEmpty() && previous >= 0) {
            currentQueueIndex = previous
            val track = currentQueue[currentQueueIndex]
            Log.d(PLAYER_LOG_TAG, "queue previous index=$currentQueueIndex id=${track.id}")
            sessionPlayer?.seekToPreviousMediaItem()
        } else {
            currentTrack?.let { playTrack(it.id, it) }
        }
    }

    private fun jumpToQueueIndex(index: Int) {
        if (index !in currentQueue.indices) {
            Log.d(PLAYER_LOG_TAG, "queue jump ignored index=$index size=${currentQueue.size}")
            return
        }
        currentQueueIndex = index
        val track = currentQueue[currentQueueIndex]
        Log.d(PLAYER_LOG_TAG, "queue jump index=$currentQueueIndex id=${track.id}")
        sessionPlayer?.seekTo(currentQueueIndex, C.TIME_UNSET)
    }

    private fun handleMediaProductEnded() {
        val track = currentTrack
        val elapsedMs = (SystemClock.elapsedRealtime() - currentTrackStartedAtMs).coerceAtLeast(0L)
        val durationMs = track?.durationMs ?: 0L
        if (shouldHoldQueueAdvanceOnEarlyMediaEnd(elapsedMs = elapsedMs, durationMs = durationMs)) {
            Log.w(
                PLAYER_LOG_TAG,
                "backend ended before catalog duration id=${track?.id.orEmpty()} elapsedMs=$elapsedMs durationMs=$durationMs; not advancing queue automatically",
            )
            sessionPlayer?.pause()
            return
        }
        Log.d(PLAYER_LOG_TAG, "media ended; ExoPlayer reached end of playlist")
    }

    private fun logPlaybackContext(label: String, context: PlaybackDiagnosticContext) {
        Log.d(
            PLAYER_LOG_TAG,
            "backend context $label productId=${context.productId} presentation=${context.assetPresentation} previewReason=${context.previewReason.orEmpty()} durationSec=${context.durationSeconds} quality=${context.audioQuality.orEmpty()} codec=${context.audioCodec.orEmpty()}",
        )
    }

    private suspend fun configureQuality() {
        val preset = runCatching {
            sharedSettingsDataStore.data.first()[stringPreferencesKey("audio_preset")]
                ?.let { AudioPreset.valueOf(it) }
        }.getOrNull() ?: AudioPreset.BatterySaver
        playbackBackend?.setAudioPreset(preset)
    }

    private fun playerActivityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent().setClassName(packageName, "com.tidal.wear.PlayerActivity"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(PLAYER_LOG_TAG, "onTaskRemoved isPlaying=${sessionPlayer?.isPlaying == true}")
        if (sessionPlayer?.isPlaying != true) stopSelf()
    }

    override fun onDestroy() {
        Log.d(PLAYER_LOG_TAG, "onDestroy isPlaying=${sessionPlayer?.isPlaying == true} track=${currentTrack?.id.orEmpty()} queueSize=${currentQueue.size} queueIndex=$currentQueueIndex")
        session?.release()
        session = null
        sessionPlayer = null
        playbackBackend?.release()
        playbackBackend = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun fixtureTrack() = TidalTrack(
        id = "fixture-run-01",
        title = "TIDAL Preview",
        artist = "Wear OS",
        album = "Fixture",
        durationMs = 30_000L,
    )
}

private fun Intent.toTrackOrNull(): TidalTrack? {
    val id = getStringExtra(PlaybackActions.EXTRA_TRACK_ID)?.takeIf { it.isNotBlank() } ?: return null
    val title = getStringExtra(PlaybackActions.EXTRA_TITLE).orEmpty()
    if (title.isBlank()) return null
    return TidalTrack(
        id = id,
        title = title,
        artist = getStringExtra(PlaybackActions.EXTRA_ARTIST).orEmpty(),
        album = getStringExtra(PlaybackActions.EXTRA_ALBUM).orEmpty(),
        artworkUrl = getStringExtra(PlaybackActions.EXTRA_ARTWORK_URL),
        durationMs = getLongExtra(PlaybackActions.EXTRA_DURATION_MS, 0L),
        albumId = getStringExtra(PlaybackActions.EXTRA_ALBUM_ID).orEmpty(),
        artistId = getStringExtra(PlaybackActions.EXTRA_ARTIST_ID).orEmpty(),
    )
}
