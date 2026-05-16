package com.tidal.wear.core.playback

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.AudioPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
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
    private var audioPreset = AudioPreset.BatterySaver
    private var currentManifest: ResolvedManifest? = null
    private val player = ExoPlayer.Builder(appContext).build().also { exo ->
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _events.tryEmit(PlaybackBackendEvent.StateChanged(playbackState.toBackendState(exo.isPlaying)))
                    if (playbackState == Player.STATE_ENDED) {
                        currentManifest?.let { _events.tryEmit(PlaybackBackendEvent.MediaEnded(it.toDiagnosticContext(exo.duration))) }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _events.tryEmit(
                        PlaybackBackendEvent.StateChanged(
                            if (isPlaying) PlaybackBackendState.Playing else PlaybackBackendState.NotPlaying,
                        ),
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    _events.tryEmit(PlaybackBackendEvent.Error(error.errorCodeName, error))
                }
            },
        )
    }

    override val events: Flow<PlaybackBackendEvent> = _events.asSharedFlow()
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)

    override fun setAudioPreset(preset: AudioPreset) {
        audioPreset = preset
    }

    override fun loadTrack(trackId: String) {
        scope.launch {
            runCatching {
                val manifest = withContext(Dispatchers.IO) { fetchPlaybackManifest(trackId) }
                currentManifest = manifest
                Log.d(DIRECT_LOG_TAG, "manifest loaded id=$trackId source=${manifest.source} presentation=${manifest.presentation} previewReason=${manifest.previewReason.orEmpty()} mime=${manifest.mimeType} codec=${manifest.codec.orEmpty()}")
                val mediaSource = when (manifest.kind) {
                    ManifestKind.Dash -> DashMediaSource.Factory(DefaultDataSource.Factory(appContext))
                        .createMediaSource(manifest.mediaItem(trackId))
                    ManifestKind.DirectUrl -> ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext))
                        .createMediaSource(manifest.mediaItem(trackId))
                }
                player.setMediaSource(mediaSource)
                player.prepare()
                _events.emit(PlaybackBackendEvent.MediaTransition(manifest.toDiagnosticContext(player.duration)))
                _events.emit(PlaybackBackendEvent.QualityChanged(manifest.toDiagnosticContext(player.duration)))
            }.onFailure { error ->
                Log.e(DIRECT_LOG_TAG, "manifest playback load failed id=$trackId", error)
                _events.emit(PlaybackBackendEvent.Error(error::class.java.simpleName, error))
            }
        }
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
        player.release()
    }

    private fun fetchPlaybackManifest(trackId: String): ResolvedManifest {
        val token = kotlinx.coroutines.runBlocking { authRepository.getAccessToken() }
            ?: throw IOException("No TIDAL access token")
        val clientId = kotlinx.coroutines.runBlocking { authRepository.getClientIdForApi() }
        runCatching { fetchDesktopPlaybackInfo(trackId, token, clientId) }
            .onFailure { Log.w(DIRECT_LOG_TAG, "desktop playbackinfo failed id=$trackId reason=${it::class.java.simpleName}: ${it.message}") }
            .getOrNull()
            ?.let { return it }

        val urlBuilder = "https://openapi.tidal.com/v2/trackManifests/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("manifestType", "MPEG_DASH")
            .addQueryParameter("uriScheme", "DATA")
            .addQueryParameter("usage", "PLAYBACK")
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
            return body.toTrackManifest(trackId)
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
)

private fun ResolvedManifest.mediaItem(trackId: String): MediaItem = MediaItem.Builder()
    .setMediaId(trackId)
    .setUri(Uri.parse(uri))
    .setMimeType(if (kind == ManifestKind.Dash) MimeTypes.APPLICATION_MPD else mimeType.takeIf { it.isNotBlank() })
    .build()

private fun String.toTrackManifest(trackId: String): ResolvedManifest {
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
        source = "openapi-trackManifests",
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

private fun Int.toBackendState(isPlaying: Boolean): PlaybackBackendState = when (this) {
    Player.STATE_BUFFERING -> PlaybackBackendState.Stalled
    Player.STATE_READY -> if (isPlaying) PlaybackBackendState.Playing else PlaybackBackendState.NotPlaying
    Player.STATE_ENDED,
    Player.STATE_IDLE,
    -> PlaybackBackendState.Idle
    else -> PlaybackBackendState.NotPlaying
}

private const val DIRECT_LOG_TAG = "Untidy/DirectPlayback"
