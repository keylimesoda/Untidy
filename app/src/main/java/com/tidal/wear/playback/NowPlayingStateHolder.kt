package com.tidal.wear.playback

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.TidalMediaService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlaybackSource { Live, Fixture }

data class NowPlayingUiState(
    val track: TidalTrack? = null,
    val source: PlaybackSource = PlaybackSource.Fixture,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 0,
    val maxVolume: Int = 1,
    val error: String? = null,
)

class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {
    private val holder = NowPlayingStateHolder(application.applicationContext, viewModelScope)
    val state: StateFlow<NowPlayingUiState> = holder.state

    fun togglePlayPause() = holder.togglePlayPause()
    fun seekToPrevious() = holder.seekToPrevious()
    fun seekToNext() = holder.seekToNext()
    fun seekTo(ms: Long) = holder.seekTo(ms)
    fun setVolumeFraction(f: Float) = holder.setVolumeFraction(f)

    override fun onCleared() {
        holder.release()
    }
}

class NowPlayingStateHolder(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(
        NowPlayingUiState(
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
        ),
    )
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

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
        controller?.seekToPreviousMediaItem()
    }

    fun seekToNext() {
        controller?.seekToNextMediaItem()
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

    private fun updateFromPlayer(player: Player) {
        val metadata = player.mediaMetadata
        val mediaId = player.currentMediaItem?.mediaId.orEmpty()
        val duration = player.duration.takeIf { it > 0 } ?: metadata.durationMs ?: 0L
        _state.value = _state.value.copy(
            track = if (mediaId.isNotBlank() || metadata.title != null) {
                TidalTrack(
                    id = mediaId.ifBlank { "tidal-current" },
                    title = metadata.title?.toString().orEmpty().ifBlank { "Untidy" },
                    artist = metadata.artist?.toString().orEmpty(),
                    album = metadata.albumTitle?.toString().orEmpty(),
                    artworkUrl = metadata.artworkUri?.toString(),
                    durationMs = duration,
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
            error = null,
        )
    }

    private fun restartPositionPolling(isPlaying: Boolean) {
        positionJob?.cancel()
        if (!isPlaying) return
        positionJob = scope.launch {
            while (isActive) {
                controller?.let(::updateFromPlayer)
                delay(500)
            }
        }
    }
}
