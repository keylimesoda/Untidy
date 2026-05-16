package com.tidal.wear.core.playback

import android.app.Application
import android.content.Context
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.common.model.AudioQuality
import com.tidal.sdk.player.common.model.MediaProduct
import com.tidal.sdk.player.common.model.ProductType
import com.tidal.sdk.player.playbackengine.model.Event
import com.tidal.sdk.player.playbackengine.model.PlaybackContext
import com.tidal.sdk.player.playbackengine.model.PlaybackState
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.AudioPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal enum class PlaybackBackendState {
    Idle,
    Playing,
    NotPlaying,
    Stalled,
}

internal data class PlaybackDiagnosticContext(
    val productId: String,
    val assetPresentation: String,
    val durationSeconds: Float,
    val previewReason: String?,
    val audioQuality: String?,
    val audioCodec: String?,
)

internal sealed interface PlaybackBackendEvent {
    data class StateChanged(val state: PlaybackBackendState) : PlaybackBackendEvent
    data class MediaTransition(val context: PlaybackDiagnosticContext) : PlaybackBackendEvent
    data class MediaEnded(val context: PlaybackDiagnosticContext) : PlaybackBackendEvent
    data class QualityChanged(val context: PlaybackDiagnosticContext) : PlaybackBackendEvent
    data class Error(val code: String, val throwable: Throwable?) : PlaybackBackendEvent
    data class Other(val name: String) : PlaybackBackendEvent
}

internal interface PlaybackBackend {
    val events: Flow<PlaybackBackendEvent>
    val positionMs: Long

    fun setAudioPreset(preset: AudioPreset)
    fun loadTrack(trackId: String)
    fun play()
    fun pause()
    fun seek(positionSeconds: Float)
    fun release()
}

internal class TidalSdkPlaybackBackend(
    context: Context,
    scope: CoroutineScope,
    authRepository: TidalAuthRepository,
) : PlaybackBackend {
    private val eventProducer = EventProducer.getInstance(
        credentialsProvider = authRepository.credentialsProvider,
        config = EventsConfig(maxDiskUsageBytes = 1_000_000, blockedConsentCategories = emptySet(), appVersion = "0.1.0"),
        context = context,
        coroutineScope = scope,
    )
    private val sdkPlayer = com.tidal.sdk.player.Player(
        application = context.applicationContext as Application,
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
    private val _events = MutableSharedFlow<PlaybackBackendEvent>(extraBufferCapacity = 32)

    override val events: Flow<PlaybackBackendEvent> = _events.asSharedFlow()
    override val positionMs: Long
        get() = (sdkPlayer.playbackEngine.assetPosition * 1000f).toLong()

    init {
        scope.launch {
            sdkPlayer.playbackEngine.events.collect { event ->
                _events.emit(event.toBackendEvent())
            }
        }
    }

    override fun setAudioPreset(preset: AudioPreset) {
        val quality = when (preset) {
            AudioPreset.BatterySaver -> AudioQuality.LOW
            AudioPreset.Balanced,
            AudioPreset.High,
            -> AudioQuality.HIGH
        }
        sdkPlayer.playbackEngine.streamingWifiAudioQuality = quality
        sdkPlayer.playbackEngine.streamingCellularAudioQuality = quality
    }

    override fun loadTrack(trackId: String) {
        sdkPlayer.playbackEngine.load(MediaProduct(ProductType.TRACK, trackId))
    }

    override fun play() {
        sdkPlayer.playbackEngine.play()
    }

    override fun pause() {
        sdkPlayer.playbackEngine.pause()
    }

    override fun seek(positionSeconds: Float) {
        sdkPlayer.playbackEngine.seek(positionSeconds)
    }

    override fun release() {
        sdkPlayer.release()
    }
}

private fun Event.toBackendEvent(): PlaybackBackendEvent = when (this) {
    is Event.PlaybackStateChange -> PlaybackBackendEvent.StateChanged(playbackState.toBackendState())
    is Event.MediaProductTransition -> PlaybackBackendEvent.MediaTransition(playbackContext.toDiagnosticContext())
    is Event.MediaProductEnded -> PlaybackBackendEvent.MediaEnded(playbackContext.toDiagnosticContext())
    is Event.PlaybackQualityChanged -> PlaybackBackendEvent.QualityChanged(playbackContext.toDiagnosticContext())
    is Event.Error -> PlaybackBackendEvent.Error(errorCode, this)
    else -> PlaybackBackendEvent.Other(this::class.simpleName.orEmpty())
}

private fun PlaybackState.toBackendState(): PlaybackBackendState = when (this) {
    PlaybackState.IDLE -> PlaybackBackendState.Idle
    PlaybackState.PLAYING -> PlaybackBackendState.Playing
    PlaybackState.NOT_PLAYING -> PlaybackBackendState.NotPlaying
    PlaybackState.STALLED -> PlaybackBackendState.Stalled
}

private fun PlaybackContext.toDiagnosticContext(): PlaybackDiagnosticContext = when (this) {
    is PlaybackContext.Track -> PlaybackDiagnosticContext(
        productId = productId,
        assetPresentation = assetPresentation.name,
        durationSeconds = duration,
        previewReason = previewReason?.name,
        audioQuality = audioQuality?.name,
        audioCodec = audioCodec,
    )
    else -> PlaybackDiagnosticContext(
        productId = productId,
        assetPresentation = assetPresentation.name,
        durationSeconds = duration,
        previewReason = null,
        audioQuality = null,
        audioCodec = null,
    )
}
