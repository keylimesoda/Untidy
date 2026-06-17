package com.tidal.wear.playback

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.PlaybackActions
import com.tidal.wear.core.playback.TidalMediaService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

enum class PlaybackSource { Live, Fixture }

data class PlaybackQueueSnapshot(
    val items: List<TidalTrack> = emptyList(),
    val currentIndex: Int = -1,
) {
    val hasKnownQueue: Boolean = items.isNotEmpty() && currentIndex in items.indices
}

data class NowPlayingUiState(
    val track: TidalTrack? = null,
    val source: PlaybackSource = PlaybackSource.Fixture,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 0,
    val maxVolume: Int = 1,
    val error: String? = null,
    val queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
)

class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {
    private val holder = NowPlayingStateHolder(application.applicationContext, viewModelScope)
    val state: StateFlow<NowPlayingUiState> = holder.state

    fun togglePlayPause() = holder.togglePlayPause()
    fun seekToPrevious() = holder.seekToPrevious()
    fun seekToNext() = holder.seekToNext()
    fun jumpToQueueIndex(index: Int) = holder.jumpToQueueIndex(index)
    fun seekTo(ms: Long) = holder.seekTo(ms)
    fun setVolumeFraction(f: Float) = holder.setVolumeFraction(f)

    override fun onCleared() {
        holder.release()
    }
}

class NowPlayingStateHolder(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val pollPosition: Boolean = true,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(
        NowPlayingUiState(
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
        ),
    )
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateFromPlayer(player)
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            controller?.let(::updateFromPlayer)
            restartPositionPolling(isPlaying)
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            controller?.let(::updateFromPlayer)
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.message ?: error.errorCodeName)
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, TidalMediaService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture.addListener(
            {
                runCatching {
                    controller = controllerFuture.get().also {
                        it.addListener(listener)
                        updateFromPlayer(it)
                        restartPositionPolling(it.isPlaying)
                    }
                }.onFailure { error ->
                    _state.value = _state.value.copy(error = error.message ?: "Media controller unavailable")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekToPrevious() {
        sendServiceAction(PlaybackActions.ACTION_SKIP_PREVIOUS)
    }

    fun seekToNext() {
        sendServiceAction(PlaybackActions.ACTION_SKIP_NEXT)
    }

    fun jumpToQueueIndex(index: Int) {
        sendServiceAction(
            PlaybackActions.ACTION_JUMP_TO_QUEUE_INDEX,
            android.os.Bundle().apply { putInt(PlaybackActions.EXTRA_QUEUE_INDEX, index) },
        )
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun setVolumeFraction(f: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volume = (f.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        _state.value = _state.value.copy(volume = volume, maxVolume = max)
    }

    fun release() {
        positionJob?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun updateFromPlayer(player: Player) {
        val metadata = player.mediaMetadata
        val mediaId = player.currentMediaItem?.mediaId.orEmpty()
        val duration = player.duration.takeIf { it > 0 } ?: metadata.durationMs ?: 0L
        val extras = metadata.extras
        val queue = parseQueueSnapshot(extras)
        _state.value = _state.value.copy(
            track = if (mediaId.isNotBlank() || metadata.title != null) {
                TidalTrack(
                    id = mediaId.ifBlank { "tidal-current" },
                    title = metadata.title?.toString().orEmpty().ifBlank { "Untidy" },
                    artist = metadata.artist?.toString().orEmpty(),
                    album = metadata.albumTitle?.toString().orEmpty(),
                    artworkUrl = metadata.artworkUri?.toString(),
                    durationMs = duration,
                    albumId = extras?.getString(PlaybackActions.EXTRA_ALBUM_ID).orEmpty(),
                    artistId = extras?.getString(PlaybackActions.EXTRA_ARTIST_ID).orEmpty(),
                )
            } else {
                null
            },
            source = if (mediaId.startsWith("fixture")) PlaybackSource.Fixture else PlaybackSource.Live,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            error = player.playerError?.let { it.message ?: it.errorCodeName },
            queue = queue,
        )
    }

    private fun parseQueueSnapshot(extras: android.os.Bundle?): PlaybackQueueSnapshot {
        val payload = extras?.getString(PlaybackActions.EXTRA_QUEUE_PAYLOAD).orEmpty()
        val items = runCatching { json.decodeFromString<List<TidalTrack>>(payload) }.getOrDefault(emptyList())
        val currentIndex = extras?.getInt(PlaybackActions.EXTRA_QUEUE_INDEX, -1) ?: -1
        return PlaybackQueueSnapshot(items = items, currentIndex = currentIndex)
    }

    private fun restartPositionPolling(isPlaying: Boolean) {
        positionJob?.cancel()
        positionJob = null
        if (!pollPosition) return
        if (!isPlaying) return
        positionJob = scope.launch {
            while (isActive) {
                controller?.let(::updateFromPlayer)
                delay(500)
            }
        }
    }

    private fun sendServiceAction(action: String, extras: android.os.Bundle? = null) {
        ContextCompat.startForegroundService(
            context,
            android.content.Intent(context, TidalMediaService::class.java)
                .setAction(action)
                .apply { extras?.let(::putExtras) },
        )
    }
}
