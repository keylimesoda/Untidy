package com.tidal.wear.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.ScreenScaffold
import com.google.android.horologist.compose.rotaryinput.RotaryInputConfigDefaults
import com.google.android.horologist.compose.rotaryinput.accumulatedBehavior
import com.tidal.wear.BuildConfig
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.core.playback.offline.markOfflineTrackDownloaded
import com.tidal.wear.core.playback.offline.removeOfflineTrackDownload
import com.tidal.wear.playback.NowPlayingUiState
import com.tidal.wear.playback.NowPlayingViewModel
import com.tidal.wear.ui.art.AlbumArt
import com.tidal.wear.ui.art.rememberAlbumArt
import com.tidal.wear.ui.components.AlbumArtCard
import com.tidal.wear.ui.components.CircularPerimeterProgress
import com.tidal.wear.ui.components.VolumeOverlay
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TidalPlayerScreen(
    viewModel: NowPlayingViewModel,
    isAmbient: Boolean,
    ambientOffset: Pair<Int, Int>,
    deviceHasLowBitAmbient: Boolean,
    burnInProtectionRequired: Boolean,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val albumArt = if (isAmbient) null else rememberAlbumArt(state.track)
    val accent = if (albumArt?.bitmap != null) albumArt.palette.accentColor() else TidalColors.Cyan
    val context = LocalContext.current
    val authRepository = remember(context) { TidalAuthRepositoryProvider.get(context.applicationContext) }
    val apiClient = remember(authRepository) { TidalApiClient(authRepository) }
    val currentTrack = state.track
    var downloadState by remember(currentTrack?.id) {
        mutableStateOf(context.initialDownloadState(currentTrack))
    }
    var downloadProofRun by remember(currentTrack?.id) { mutableIntStateOf(0) }

    LaunchedEffect(downloadProofRun, currentTrack?.id) {
        val track = currentTrack
        if (downloadProofRun <= 0 || track?.isDownloadProofEligible() != true) return@LaunchedEffect
        downloadState = DownloadState.Downloading(0.02f)
        repeat(36) { attempt ->
            delay(1_000)
            val proof = withContext(Dispatchers.IO) { context.latestOfflineDownloadProofFor(track.id) }
            if (proof == true) {
                withContext(Dispatchers.IO) { context.markOfflineTrackDownloaded(track) }
                downloadState = DownloadState.Downloaded
                return@LaunchedEffect
            }
            downloadState = DownloadState.Downloading(((attempt + 1).toFloat() / 36f).coerceIn(0.02f, 0.95f))
        }
        downloadState = DownloadState.NotDownloaded
        Toast.makeText(context, "Offline download did not finish", Toast.LENGTH_SHORT).show()
    }

    fun startDebugDownload() {
        val track = currentTrack
        when {
            !BuildConfig.DEBUG -> Toast.makeText(context, "Offline downloads are not available in this build", Toast.LENGTH_LONG).show()
            track?.isDownloadProofEligible() != true -> Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
            downloadState is DownloadState.Downloading -> Unit
            else -> {
                runCatching {
                    context.startActivity(
                        Intent().setClassName(context.packageName, "com.tidal.wear.debug.OfflineProofActivity")
                            .putExtra("trackId", track.id)
                            .putExtra("countryCode", "US")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    downloadProofRun += 1
                }.onFailure {
                    Toast.makeText(context, "Offline download unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun removeCurrentDownload(): String? {
        val track = currentTrack ?: return "Nothing playing"
        return if (context.removeOfflineTrackDownload(track.id)) {
            downloadState = DownloadState.NotDownloaded
            Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
            "Download removed"
        } else {
            "Couldn't remove download"
        }
    }

    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    if (isAmbient) {
        TidalPlayerAmbient(
            track = state.track,
            isPlaying = state.isPlaying,
            ambientOffset = ambientOffset,
            deviceHasLowBitAmbient = deviceHasLowBitAmbient,
            burnInProtectionRequired = burnInProtectionRequired,
        )
    } else {
        TidalPlayerNonAmbient(
            state = state,
            albumArt = albumArt,
            accent = accent,
            viewModel = viewModel,
            apiClient = apiClient,
            downloadState = downloadState,
            onDownload = ::startDebugDownload,
            onRemoveDownload = ::removeCurrentDownload,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TidalPlayerNonAmbient(
    state: NowPlayingUiState,
    albumArt: AlbumArt?,
    accent: Color,
    viewModel: NowPlayingViewModel,
    apiClient: TidalApiClient,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onRemoveDownload: () -> String?,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val context = LocalContext.current
    val scale = playerLayoutScale()
    val density = LocalDensity.current
    var showVolume by remember { mutableStateOf(false) }
    var volumeChangePulse by remember { mutableIntStateOf(0) }
    var liked by remember(state.track?.id) { mutableStateOf(false) }
    var likeLoading by remember(state.track?.id) { mutableStateOf(false) }
    var likeError by remember(state.track?.id) { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<TidalTrack?>(null) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(volumeChangePulse) {
        if (volumeChangePulse > 0) {
            delay(1_500)
            showVolume = false
        }
    }
    LaunchedEffect(state.track?.id) {
        val trackId = state.track?.id
        likeError = null
        if (trackId.isNullOrBlank()) {
            liked = false
            likeLoading = false
            return@LaunchedEffect
        }
        likeLoading = true
        runCatching { apiClient.isFavoriteTrack(trackId) }
            .onSuccess { liked = it }
            .onFailure { likeError = "Like unavailable" }
        likeLoading = false
    }
    addToPlaylistTrack?.let { trackSnapshot ->
        AddToPlaylistSheet(
            track = trackSnapshot,
            apiClient = apiClient,
            onBack = { addToPlaylistTrack = null },
        )
        return
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentTrack = state.track,
            onJumpToIndex = { index ->
                viewModel.jumpToQueueIndex(index)
                showQueue = false
                scope.launch { pagerState.animateScrollToPage(0) }
            },
            onBack = { showQueue = false },
        )
        return
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(TidalColors.Black),
    ) { page ->
        when (page) {
            0 -> ScreenScaffold(timeText = {}) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TidalColors.Black)
                        .focusRequester(focusRequester)
                        .focusable()
                        .rotaryScrollable(
                            behavior = accumulatedBehavior(
                                rateLimitCoolDownMs = 2 * RotaryInputConfigDefaults.DEFAULT_RATE_LIMIT_COOL_DOWN_MS,
                                onValueChange = { delta ->
                                    val maxVolume = state.maxVolume.coerceAtLeast(1)
                                    val step = if (delta > 0) 1 else -1
                                    val nextVolume = (state.volume + step).coerceIn(0, maxVolume)
                                    if (nextVolume != state.volume) {
                                        viewModel.setVolumeFraction(nextVolume.toFloat() / maxVolume.toFloat())
                                        showVolume = true
                                        volumeChangePulse += 1
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                },
                            ),
                            focusRequester = focusRequester,
                        ),
                ) {
        CircularPerimeterProgress(
            progress = progress,
            modifier = Modifier.fillMaxSize().padding(3.dp * scale),
            progressColor = accent,
            strokeWidth = 2.dp * scale,
        )

        AlbumArtCard(
            bitmap = albumArt?.bitmap,
            accent = accent,
            sizeDp = (68 * scale).roundToInt(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 12.dp * scale)
                .clickable(onClick = viewModel::togglePlayPause),
            cornerRadius = 6.dp,
        )

        LikeIconAt(
            liked = liked,
            enabled = state.track != null && !likeLoading,
            description = when {
                likeLoading -> "Loading like state"
                liked -> "Unlike"
                else -> "Like"
            },
            x = 10,
            y = 36,
            scale = scale,
            onClick = {
                val trackId = state.track?.id ?: return@LikeIconAt
                if (likeLoading) return@LikeIconAt
                if (!view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                val target = !liked
                liked = target
                likeLoading = true
                likeError = null
                scope.launch {
                    runCatching { apiClient.setFavoriteTrack(trackId, target) }
                        .onSuccess { persisted -> liked = persisted }
                        .onFailure {
                            liked = !target
                            likeError = "Like failed"
                            Toast.makeText(context, "Couldn't update like", Toast.LENGTH_SHORT).show()
                        }
                    likeLoading = false
                }
            },
        )
        SecondaryIconAt(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            description = "Queue",
            x = 134,
            y = 36,
            scale = scale,
            onClick = { showQueue = true },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 82.dp * scale)
                .fillMaxWidth()
                .height(60.dp * scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0f),
                        ),
                        radius = with(density) { (120.dp * scale).toPx() },
                    ),
                ),
        )

        Text(
            text = state.track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.title3,
            color = TidalColors.White,
            fontWeight = FontWeight.Black,
            fontSize = (18 * scale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 86.dp * scale)
                .width(140.dp * scale),
        )
        Text(
            text = state.track?.artist?.ifBlank { "Untidy" } ?: "Tap Start listening",
            style = MaterialTheme.typography.caption1,
            color = TidalColors.OnSurfaceMuted,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * scale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 108.dp * scale)
                .width(140.dp * scale),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 123.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(4.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(Icons.Filled.SkipPrevious, "Previous", visualSize = 28, iconSize = 16, scale = scale, onClick = viewModel::seekToPrevious)
            TransportButton(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (state.isPlaying) "Pause" else "Play",
                visualSize = 36,
                iconSize = 20,
                scale = scale,
                onClick = viewModel::togglePlayPause,
                isPlayPause = true,
                playPauseColor = accent,
            )
            TransportButton(Icons.Filled.SkipNext, "Next", visualSize = 28, iconSize = 16, scale = scale, onClick = viewModel::seekToNext)
        }

        (state.error ?: likeError)?.takeIf { it.isNotBlank() }?.let { error ->
            Text(
                text = error,
                color = Color(0xFFFF8A80),
                fontSize = (10 * scale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp * scale)
                    .offset(y = (-20).dp * scale),
            )
        }

        ActionsHint(
            scale = scale,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp * scale),
            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
        )

        VolumeOverlay(
            visible = showVolume,
            volume = state.volume,
            maxVolume = state.maxVolume,
            accent = accent,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
        )
                }
            }
            1 -> ScreenScaffold(timeText = {}) {
                ActionsSheet(
                    downloadState = downloadState,
                    outputOptions = rememberAudioOutputOptions(),
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onQueue = { showQueue = true },
                    onOutputSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }.onFailure {
                            Toast.makeText(context, "Open Wear OS Bluetooth settings to change output", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddToPlaylist = {
                        val track = state.track
                        when {
                            track == null -> "Nothing playing"
                            track.id.isBlank() || track.id == "tidal-current" || track.id.startsWith("fixture", ignoreCase = true) -> "Track unavailable"
                            else -> {
                                addToPlaylistTrack = track
                                null
                            }
                        }
                    },
                    onViewAlbum = {
                        val albumId = state.track?.albumId.orEmpty()
                        if (albumId.isBlank()) {
                            "Album unavailable"
                        } else {
                            onOpenAlbum(albumId)
                            null
                        }
                    },
                    onViewArtist = {
                        val artistId = state.track?.artistId.orEmpty()
                        if (artistId.isBlank()) {
                            "Artist unavailable"
                        } else {
                            onOpenArtist(artistId)
                            null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LikeIconAt(
    liked: Boolean,
    enabled: Boolean,
    description: String,
    x: Int,
    y: Int,
    scale: Float,
    onClick: () -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (liked) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "likeScale",
    )
    val tint by animateColorAsState(
        targetValue = if (liked) TidalColors.Cyan else TidalColors.White,
        animationSpec = tween(200),
        label = "likeTint",
    )
    Box(
        modifier = Modifier
            .offset(x = x.dp * scale, y = y.dp * scale)
            .size(48.dp * scale)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = liked,
            animationSpec = tween(150),
            label = "likeIcon",
        ) { isLiked ->
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = description,
                tint = if (enabled) tint else TidalColors.OnSurfaceMuted,
                modifier = Modifier
                    .size(24.dp * scale)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
    }
}

@Composable
private fun SecondaryIconAt(
    icon: ImageVector,
    description: String,
    x: Int,
    y: Int,
    scale: Float,
    tint: Color = TidalColors.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(x = x.dp * scale, y = y.dp * scale)
            .size(48.dp * scale)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp * scale))
    }
}

@Composable
private fun ActionsHint(
    scale: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Actions" }
            .padding(horizontal = 8.dp * scale, vertical = 3.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(2.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Actions",
            color = TidalColors.White,
            fontSize = (9 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(12.dp * scale),
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    visualSize: Int,
    iconSize: Int,
    scale: Float,
    onClick: () -> Unit,
    isPlayPause: Boolean = false,
    playPauseColor: Color = TidalColors.Cyan,
) {
    Box(
        modifier = Modifier
            .size(48.dp * scale)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize.dp * scale)
                .clip(CircleShape)
                .background(if (isPlayPause) playPauseColor else TidalColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPlayPause) TidalColors.Black else TidalColors.White,
                modifier = Modifier.size(iconSize.dp * scale),
            )
        }
    }
}


private fun Context.initialDownloadState(track: TidalTrack?): DownloadState = when {
    !BuildConfig.DEBUG -> DownloadState.Unavailable
    track?.isDownloadProofEligible() != true -> DownloadState.Unavailable
    isOfflineTrackDownloaded(track.id) -> DownloadState.Downloaded
    else -> DownloadState.NotDownloaded
}

private fun TidalTrack.isDownloadProofEligible(): Boolean = id.isNotBlank() &&
    id != "tidal-current" &&
    !id.startsWith("fixture", ignoreCase = true)

private fun Context.latestOfflineDownloadProofFor(trackId: String): Boolean? = runCatching {
    val latest = File(filesDir, "offline-proof/latest.json")
    if (!latest.isFile) return@runCatching null
    val root = JSONObject(latest.readText())
    if (root.optString("trackId") != trackId) return@runCatching null
    val events = root.optJSONArray("events") ?: return@runCatching null
    for (index in events.length() - 1 downTo 0) {
        val event = events.optJSONObject(index) ?: continue
        if (event.optString("name") != "downloadManifestNetworkDisabledReplay") continue
        val fields = event.optJSONObject("fields") ?: continue
        return@runCatching fields.optBoolean("playbackClaimed", false) &&
            fields.optBoolean("reachedReady", false) &&
            !fields.optBoolean("offlineUpstreamAttempted", true)
    }
    null
}.getOrNull()

@Composable
private fun TidalPlayerAmbient(
    track: TidalTrack?,
    isPlaying: Boolean,
    ambientOffset: Pair<Int, Int>,
    deviceHasLowBitAmbient: Boolean,
    burnInProtectionRequired: Boolean,
) {
    val scale = playerLayoutScale()
    val primary = if (deviceHasLowBitAmbient) Color.White else Color.White.copy(alpha = 0.82f)
    val secondary = if (deviceHasLowBitAmbient) Color.White else Color.White.copy(alpha = 0.56f)
    val offset = if (burnInProtectionRequired) ambientOffset else 0 to 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TidalColors.Black)
            .graphicsLayer {
                translationX = offset.first.toFloat()
                translationY = offset.second.toFloat()
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp * scale, vertical = 36.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track?.title ?: "Untidy",
                style = MaterialTheme.typography.caption1,
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = track?.artist?.takeIf { it.isNotBlank() } ?: if (isPlaying) "Playing" else "Paused",
                color = secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                text = if (isPlaying) "Playing" else "Paused",
                color = secondary,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = if (deviceHasLowBitAmbient) 24.sp else 28.sp,
                fontWeight = FontWeight.Light,
                color = primary,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun rememberAudioOutputOptions(): List<AudioOutputOption> {
    val context = LocalContext.current
    return remember(context) { context.audioOutputOptions() }
}

private fun Context.audioOutputOptions(): List<AudioOutputOption> {
    val audioManager = getSystemService(AudioManager::class.java) ?: return listOf(AudioOutputOption("System", true))
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type.isWearOutputType() }
        .distinctBy { "${it.type}:${it.productName}" }
    val preferred = devices.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: devices.firstOrNull()
    return devices.map { device ->
        AudioOutputOption(
            label = device.outputLabel(),
            preferred = device == preferred,
        )
    }.ifEmpty { listOf(AudioOutputOption("System", true)) }
}

private fun Int.isWearOutputType(): Boolean = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    -> true
    else -> false
}

private fun AudioDeviceInfo.outputLabel(): String {
    val product = productName?.toString()?.takeIf { it.isNotBlank() }
    val typeLabel = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        -> "Bluetooth"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Watch speaker"
        else -> "Output"
    }
    return product?.takeUnless { it.equals(typeLabel, ignoreCase = true) } ?: typeLabel
}

@Composable
private fun playerLayoutScale(): Float {
    val configuration = LocalConfiguration.current
    return (configuration.screenWidthDp / 192f).coerceIn(0.86f, 1.25f)
}



