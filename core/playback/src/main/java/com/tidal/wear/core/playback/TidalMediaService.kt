package com.tidal.wear.core.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
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
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.common.model.AudioQuality
import com.tidal.sdk.player.common.model.MediaProduct
import com.tidal.sdk.player.common.model.ProductType
import com.tidal.sdk.player.playbackengine.model.Event
import com.tidal.sdk.player.playbackengine.model.PlaybackState
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

    private var tidalSdkPlayer: com.tidal.sdk.player.Player? = null
    private var sessionPlayer: TidalSessionPlayer? = null
    private var session: MediaLibrarySession? = null
    private var currentTrack: TidalTrack? = null
    private lateinit var apiClient: TidalApiClient
    private lateinit var authRepository: com.tidal.wear.core.auth.TidalAuthRepository

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        authRepository = TidalAuthRepositoryProvider.get(this)
        apiClient = TidalApiClient(authRepository)
        val eventProducer = EventProducer.getInstance(
            credentialsProvider = authRepository.credentialsProvider,
            config = EventsConfig(maxDiskUsageBytes = 1_000_000, blockedConsentCategories = emptySet(), appVersion = "0.1.0"),
            context = this,
            coroutineScope = serviceScope,
        )
        val sdkPlayer = com.tidal.sdk.player.Player(
            application = application,
            credentialsProvider = authRepository.credentialsProvider,
            eventSender = eventProducer.eventSender,
            useLibflacAudioRenderer = false,
            enableDecoderFallback = true,
            version = "0.1.0",
        ).also { player ->
            player.playbackEngine.enableAdaptive = false
            player.playbackEngine.streamingWifiAudioQuality = AudioQuality.LOW
            player.playbackEngine.streamingCellularAudioQuality = AudioQuality.LOW
        }
        tidalSdkPlayer = sdkPlayer
        sessionPlayer = TidalSessionPlayer(
            sdkPlayer = sdkPlayer,
            scope = serviceScope,
        ).also { player ->
            serviceScope.launch {
                sdkPlayer.playbackEngine.events.collect { event ->
                    Log.d(PLAYER_LOG_TAG, "SDK event: ${event::class.simpleName}")
                    if (event is Event.PlaybackStateChange) {
                        Log.d(PLAYER_LOG_TAG, "SDK state: ${event.playbackState}")
                        player.setSdkPlaybackState(event.playbackState)
                    } else if (event::class.simpleName?.contains("Error", ignoreCase = true) == true) {
                        Log.e(PLAYER_LOG_TAG, "SDK playback error event: ${event::class.simpleName}")
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
                publishOngoingActivity(
                    knownTrack ?: TidalTrack(id = trackId, title = "Untidy", artist = "TIDAL", album = ""),
                    isPlaying = true,
                )
                playTrack(trackId = trackId, knownTrack = knownTrack)
            }
            PlaybackActions.ACTION_PAUSE -> { sessionPlayer?.pause(); currentTrack?.let { publishOngoingActivity(it, isPlaying = false) } }
            PlaybackActions.ACTION_RESUME -> { sessionPlayer?.play(); currentTrack?.let { publishOngoingActivity(it, isPlaying = true) } }
            PlaybackActions.ACTION_SKIP_NEXT -> sessionPlayer?.seekToNextMediaItem()
            PlaybackActions.ACTION_SKIP_PREVIOUS -> sessionPlayer?.seekToPreviousMediaItem()
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
            sessionPlayer?.loadTrack(track)
            publishOngoingActivity(track, isPlaying = true)
            runCatching {
                Log.d(PLAYER_LOG_TAG, "SDK load start id=$id")
                tidalSdkPlayer?.playbackEngine?.load(MediaProduct(ProductType.TRACK, id))
                Log.d(PLAYER_LOG_TAG, "SDK load completed id=$id")
            }.onFailure {
                Log.e(PLAYER_LOG_TAG, "SDK load failed", it)
            }
            runCatching {
                Log.d(PLAYER_LOG_TAG, "SDK play start")
                tidalSdkPlayer?.playbackEngine?.play()
                Log.d(PLAYER_LOG_TAG, "SDK play completed")
            }.onFailure {
                Log.e(PLAYER_LOG_TAG, "SDK play failed", it)
            }
            sessionPlayer?.setSdkPlaybackState(PlaybackState.PLAYING)
        }
    }

    private suspend fun configureQuality() {
        val preset = runCatching {
            playbackSettingsDataStore.data.first()[stringPreferencesKey("audio_preset")]
                ?.let { AudioPreset.valueOf(it) }
        }.getOrNull() ?: AudioPreset.BatterySaver
        val quality = when (preset) {
            AudioPreset.BatterySaver -> AudioQuality.LOW
            AudioPreset.Balanced, AudioPreset.High -> AudioQuality.HIGH
        }
        tidalSdkPlayer?.playbackEngine?.streamingWifiAudioQuality = quality
        tidalSdkPlayer?.playbackEngine?.streamingCellularAudioQuality = quality
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
        tidalSdkPlayer?.release()
        tidalSdkPlayer = null
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
    private val sdkPlayer: com.tidal.sdk.player.Player,
    private val scope: CoroutineScope,
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

    fun setSdkPlaybackState(state: PlaybackState) {
        Log.d(PLAYER_LOG_TAG, "SessionPlayer setSdkPlaybackState $state")
        playbackState = when (state) {
            PlaybackState.IDLE -> Player.STATE_IDLE
            PlaybackState.PLAYING -> Player.STATE_READY
            PlaybackState.NOT_PLAYING -> Player.STATE_READY
            PlaybackState.STALLED -> Player.STATE_BUFFERING
        }
        playWhenReady = state == PlaybackState.PLAYING
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
            sdkPlayer.playbackEngine.play()
        } else {
            basePositionMs = currentPositionInternal()
            sdkPlayer.playbackEngine.pause()
        }
        invalidateState()
        return Futures.immediateFuture(Any())
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<Any> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> sdkPlayer.playbackEngine.skipToNext()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> sdkPlayer.playbackEngine.seek(0f)
            else -> if (positionMs != C.TIME_UNSET) {
                basePositionMs = positionMs.coerceAtLeast(0L)
                baseElapsedMs = android.os.SystemClock.elapsedRealtime()
                sdkPlayer.playbackEngine.seek(basePositionMs / 1000f)
            }
        }
        invalidateState()
        return Futures.immediateFuture(Any())
    }

    override fun handleRelease(): ListenableFuture<Any> = Futures.immediateFuture(Any())

    private fun currentPositionInternal(): Long {
        val sdkPositionMs = (sdkPlayer.playbackEngine.assetPosition * 1000f).toLong()
        if (sdkPositionMs > 0L) return sdkPositionMs
        val estimated = if (playWhenReady && playbackState == Player.STATE_READY) {
            basePositionMs + (android.os.SystemClock.elapsedRealtime() - baseElapsedMs)
        } else {
            basePositionMs
        }
        return if (durationMs == C.TIME_UNSET) estimated.coerceAtLeast(0L) else estimated.coerceIn(0L, durationMs)
    }
}



