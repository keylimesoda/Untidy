package com.tidal.wear.core.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.playbackSettingsDataStore by preferencesDataStore(name = "tidal_settings")
private const val MEDIA_CHANNEL_ID = "tidal_playback"
private const val MEDIA_NOTIFICATION_ID = 42
private const val PLAYER_LOG_TAG = "Untidy/Player"

class TidalMediaService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var playbackBackend: PlaybackBackend? = null
    private var sessionPlayer: TidalSessionPlayer? = null
    private var session: MediaLibrarySession? = null
    private var currentTrack: TidalTrack? = null
    private var currentQueue: List<TidalTrack> = emptyList()
    private var currentQueueIndex: Int = 0
    private var currentTrackStartedAtMs: Long = 0L
    private lateinit var apiClient: TidalApiClient
    private lateinit var authRepository: com.tidal.wear.core.auth.TidalAuthRepository

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        authRepository = TidalAuthRepositoryProvider.get(this)
        apiClient = TidalApiClient(authRepository)
        val backend = DirectManifestPlaybackBackend(
            context = this,
            scope = serviceScope,
            authRepository = authRepository,
        )
        playbackBackend = backend
        sessionPlayer = TidalSessionPlayer(
            playbackBackend = backend,
            onSkipNext = ::skipToNextInQueue,
            onSkipPrevious = ::skipToPreviousInQueue,
        ).also { player ->
            serviceScope.launch {
                backend.events.collect { event ->
                    Log.d(PLAYER_LOG_TAG, "backend event: ${event::class.simpleName.orEmpty()}")
                    when (event) {
                        is PlaybackBackendEvent.StateChanged -> {
                            Log.d(PLAYER_LOG_TAG, "backend state: ${event.state}")
                            player.setBackendPlaybackState(event.state)
                        }
                        is PlaybackBackendEvent.MediaTransition -> logPlaybackContext("transition", event.context)
                        is PlaybackBackendEvent.MediaEnded -> {
                            logPlaybackContext("ended", event.context)
                            handleMediaProductEnded()
                        }
                        is PlaybackBackendEvent.QualityChanged -> logPlaybackContext("quality", event.context)
                        is PlaybackBackendEvent.Error -> Log.e(PLAYER_LOG_TAG, "backend playback error event: ${event.code}", event.throwable)
                        is PlaybackBackendEvent.Other -> if (event.name.contains("Error", ignoreCase = true)) {
                            Log.e(PLAYER_LOG_TAG, "backend playback error event: ${event.name}")
                        }
                    }
                }
            }
        }
        session = MediaLibrarySession.Builder(
            this,
            sessionPlayer!!,
            object : MediaLibrarySession.Callback {},
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        Log.d(PLAYER_LOG_TAG, "onStartCommand action=${intent?.action ?: "null"}")
        when (intent?.action) {
            PlaybackActions.ACTION_PLAY_FIXTURE -> {
                publishOngoingActivity(fixtureTrack(), isPlaying = true)
                playTrack("fixture-run-01", fixtureTrack())
            }
            PlaybackActions.ACTION_PROBE_DEVICE_AUTH -> probeDeviceAuth()
            PlaybackActions.ACTION_PLAY_TRACK -> {
                val trackId = intent.getStringExtra(PlaybackActions.EXTRA_TRACK_ID).orEmpty()
                val knownTrack = intent.toTrackOrNull()
                currentQueue = emptyList()
                currentQueueIndex = 0
                publishOngoingActivity(
                    knownTrack ?: TidalTrack(id = trackId, title = "Untidy", artist = "TIDAL", album = ""),
                    isPlaying = true,
                )
                playTrack(trackId = trackId, knownTrack = knownTrack)
            }
            PlaybackActions.ACTION_PLAY_QUEUE -> playQueue(
                queueId = intent.getStringExtra(PlaybackActions.EXTRA_QUEUE_ID).orEmpty(),
                startIndex = intent.getIntExtra(PlaybackActions.EXTRA_QUEUE_START_INDEX, 0),
            )
            PlaybackActions.ACTION_PAUSE -> { sessionPlayer?.pause(); currentTrack?.let { publishOngoingActivity(it, isPlaying = false) } }
            PlaybackActions.ACTION_RESUME -> { sessionPlayer?.play(); currentTrack?.let { publishOngoingActivity(it, isPlaying = true) } }
            PlaybackActions.ACTION_SKIP_NEXT -> skipToNextInQueue()
            PlaybackActions.ACTION_SKIP_PREVIOUS -> skipToPreviousInQueue()
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
            sessionPlayer?.loadTrack(track)
            publishOngoingActivity(track, isPlaying = true)
            runCatching {
                Log.d(PLAYER_LOG_TAG, "backend load start id=$id")
                playbackBackend?.loadTrack(id)
                Log.d(PLAYER_LOG_TAG, "backend load completed id=$id")
            }.onFailure {
                Log.e(PLAYER_LOG_TAG, "backend load failed", it)
            }
            runCatching {
                Log.d(PLAYER_LOG_TAG, "backend play start")
                playbackBackend?.play()
                Log.d(PLAYER_LOG_TAG, "backend play completed")
            }.onFailure {
                Log.e(PLAYER_LOG_TAG, "backend play failed", it)
            }
            sessionPlayer?.setBackendPlaybackState(PlaybackBackendState.Playing)
        }
    }

    private fun playQueue(queueId: String, startIndex: Int) {
        val queue = PlaybackQueueStore.get(queueId)
        if (queue.isEmpty()) {
            Log.w(PLAYER_LOG_TAG, "playQueue ignored empty queue id=$queueId")
            return
        }
        currentQueue = queue
        currentQueueIndex = startIndex.coerceIn(0, queue.lastIndex)
        val track = queue[currentQueueIndex]
        Log.d(PLAYER_LOG_TAG, "playQueue id=$queueId size=${queue.size} start=$currentQueueIndex track=${track.id}")
        playTrack(track.id, track)
    }

    private fun skipToNextInQueue() {
        val next = currentQueueIndex + 1
        if (currentQueue.isNotEmpty() && next <= currentQueue.lastIndex) {
            currentQueueIndex = next
            val track = currentQueue[currentQueueIndex]
            Log.d(PLAYER_LOG_TAG, "queue next index=$currentQueueIndex id=${track.id}")
            playTrack(track.id, track)
        } else {
            Log.d(PLAYER_LOG_TAG, "queue next unavailable size=${currentQueue.size} index=$currentQueueIndex")
        }
    }

    private fun skipToPreviousInQueue() {
        val previous = currentQueueIndex - 1
        if (currentQueue.isNotEmpty() && previous >= 0) {
            currentQueueIndex = previous
            val track = currentQueue[currentQueueIndex]
            Log.d(PLAYER_LOG_TAG, "queue previous index=$currentQueueIndex id=${track.id}")
            playTrack(track.id, track)
        } else {
            currentTrack?.let { playTrack(it.id, it) }
        }
    }

    private fun handleMediaProductEnded() {
        val track = currentTrack
        val elapsedMs = (SystemClock.elapsedRealtime() - currentTrackStartedAtMs).coerceAtLeast(0L)
        val durationMs = track?.durationMs ?: 0L
        val endedEarly = durationMs > 60_000L && elapsedMs < durationMs - 45_000L
        if (endedEarly) {
            Log.w(
                PLAYER_LOG_TAG,
                "backend ended before catalog duration id=${track?.id.orEmpty()} elapsedMs=$elapsedMs durationMs=$durationMs; not advancing queue automatically",
            )
            sessionPlayer?.setBackendPlaybackState(PlaybackBackendState.Idle)
            return
        }
        skipToNextInQueue()
    }

    private fun logPlaybackContext(label: String, context: PlaybackDiagnosticContext) {
        Log.d(
            PLAYER_LOG_TAG,
            "backend context $label productId=${context.productId} presentation=${context.assetPresentation} previewReason=${context.previewReason.orEmpty()} durationSec=${context.durationSeconds} quality=${context.audioQuality.orEmpty()} codec=${context.audioCodec.orEmpty()}",
        )
    }

    private suspend fun configureQuality() {
        val preset = runCatching {
            playbackSettingsDataStore.data.first()[stringPreferencesKey("audio_preset")]
                ?.let { AudioPreset.valueOf(it) }
        }.getOrNull() ?: AudioPreset.BatterySaver
        playbackBackend?.setAudioPreset(preset)
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MEDIA_CHANNEL_ID,
            "TIDAL playback",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager?.createNotificationChannel(channel)
    }

    private fun publishOngoingActivity(track: TidalTrack, isPlaying: Boolean) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent().setClassName(packageName, "com.tidal.wear.PlayerActivity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, MEDIA_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title.ifBlank { "TIDAL" })
            .setContentText(track.artist.ifBlank { "Wear OS" })
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", serviceAction(PlaybackActions.ACTION_SKIP_PREVIOUS, 1))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                serviceAction(if (isPlaying) PlaybackActions.ACTION_PAUSE else PlaybackActions.ACTION_RESUME, 2),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", serviceAction(PlaybackActions.ACTION_SKIP_NEXT, 3))
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2))

        val ongoingActivity = OngoingActivity.Builder(this, MEDIA_NOTIFICATION_ID, builder)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStaticIcon(android.R.drawable.ic_media_play)
            .setTouchIntent(contentIntent)
            .setStatus(Status.forPart(Status.TextPart(track.title.ifBlank { "TIDAL" })))
            .build()
        ongoingActivity.apply(this)

        startForeground(MEDIA_NOTIFICATION_ID, builder.build())
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, TidalMediaService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (sessionPlayer?.isPlaying != true) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        sessionPlayer?.release()
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
    )
}

private class TidalSessionPlayer(
    private val playbackBackend: PlaybackBackend,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var mediaItem: MediaItem? = null
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var durationMs = C.TIME_UNSET
    private var basePositionMs = 0L
    private var baseElapsedMs = android.os.SystemClock.elapsedRealtime()

    fun loadTrack(track: TidalTrack) {
        Log.d(PLAYER_LOG_TAG, "SessionPlayer loadTrack id=${track.id} title=${track.title}")
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setDurationMs(track.durationMs.takeIf { it > 0 })
            .build()
        mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()
        durationMs = track.durationMs.takeIf { it > 0 } ?: C.TIME_UNSET
        basePositionMs = 0L
        baseElapsedMs = android.os.SystemClock.elapsedRealtime()
        playbackState = Player.STATE_BUFFERING
        playWhenReady = true
        invalidateState()
    }

    fun setBackendPlaybackState(state: PlaybackBackendState) {
        Log.d(PLAYER_LOG_TAG, "SessionPlayer setBackendPlaybackState $state")
        playbackState = when (state) {
            PlaybackBackendState.Idle -> Player.STATE_IDLE
            PlaybackBackendState.Playing -> Player.STATE_READY
            PlaybackBackendState.NotPlaying -> Player.STATE_READY
            PlaybackBackendState.Stalled -> Player.STATE_BUFFERING
        }
        playWhenReady = state == PlaybackBackendState.Playing
        if (!playWhenReady) basePositionMs = currentPositionInternal()
        baseElapsedMs = android.os.SystemClock.elapsedRealtime()
        invalidateState()
    }

    override fun getState(): State {
        val item = mediaItem
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_TIMELINE)
            .build()
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(currentPositionInternal())
            .setPlaylist(
                item?.let {
                    listOf(
                        MediaItemData.Builder(it.mediaId)
                            .setMediaItem(it)
                            .setMediaMetadata(it.mediaMetadata)
                            .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000L)
                            .setIsSeekable(true)
                            .build(),
                    )
                }.orEmpty(),
            )
            .setCurrentMediaItemIndex(if (item == null) C.INDEX_UNSET else 0)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<Any> {
        this.playWhenReady = playWhenReady
        if (playWhenReady) {
            baseElapsedMs = android.os.SystemClock.elapsedRealtime()
            playbackBackend.play()
        } else {
            basePositionMs = currentPositionInternal()
            playbackBackend.pause()
        }
        invalidateState()
        return Futures.immediateFuture(Any())
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<Any> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> onSkipNext()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> onSkipPrevious()
            else -> if (positionMs != C.TIME_UNSET) {
                basePositionMs = positionMs.coerceAtLeast(0L)
                baseElapsedMs = android.os.SystemClock.elapsedRealtime()
                playbackBackend.seek(basePositionMs / 1000f)
            }
        }
        invalidateState()
        return Futures.immediateFuture(Any())
    }

    override fun handleRelease(): ListenableFuture<Any> = Futures.immediateFuture(Any())

    private fun currentPositionInternal(): Long {
        val backendPositionMs = playbackBackend.positionMs
        if (backendPositionMs > 0L) return backendPositionMs
        val estimated = if (playWhenReady && playbackState == Player.STATE_READY) {
            basePositionMs + (android.os.SystemClock.elapsedRealtime() - baseElapsedMs)
        } else {
            basePositionMs
        }
        return if (durationMs == C.TIME_UNSET) estimated.coerceAtLeast(0L) else estimated.coerceIn(0L, durationMs)
    }
}



