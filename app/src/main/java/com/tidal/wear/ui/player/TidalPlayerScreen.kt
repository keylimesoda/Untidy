package com.tidal.wear.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.util.Log
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.ScreenScaffold
import com.tidal.wear.BuildConfig
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.core.playback.offline.OfflineDownloadResult
import com.tidal.wear.core.playback.offline.OfflineTrackDownloader
import com.tidal.wear.core.playback.offline.removeOfflineTrackDownload
import com.tidal.wear.core.playback.offline.repairOfflineTrackDownload
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
    ambientTime: LocalTime = LocalTime.now(),
    onOpenHome: () -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val albumArt = if (isAmbient) null else rememberAlbumArt(state.track)
    val accent = if (albumArt?.bitmap != null) albumArt.palette.accentColor() else TidalColors.OnSurfaceMuted
    val context = LocalContext.current
    val authRepository = remember(context) { TidalAuthRepositoryProvider.get(context.applicationContext) }
    val apiClient = remember(authRepository) { TidalApiClient(authRepository) }
    val currentTrack = state.track
    var downloadState by remember(currentTrack?.id) {
        mutableStateOf(context.initialDownloadState(currentTrack))
    }
    val offlineDownloader = remember(authRepository) { OfflineTrackDownloader(context.applicationContext, authRepository) }
    val scope = rememberCoroutineScope()
    // Keep destination state above the ambient branch: ambient suspends interaction, not context.
    val navigation = viewModel<PlayerNavigationState>()

    // A write can finish while the screen is ambient. Retain its outcome until an interactive
    // frame can report it rather than losing it with the dismissed sheet.
    LaunchedEffect(isAmbient, navigation.playlistResult) {
        if (!isAmbient) {
            navigation.playlistResult?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                navigation.playlistResult = null
            }
        }
    }

    fun startTrackDownload() {
        val track = currentTrack
        when {
            track?.isDownloadEligible() != true -> Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
            downloadState is DownloadState.Downloading -> Unit
            else -> {
                downloadState = DownloadState.Downloading(0.05f)
                scope.launch {
                    when (offlineDownloader.downloadTrack(track)) {
                        OfflineDownloadResult.Downloaded -> {
                            downloadState = DownloadState.Downloaded
                            Toast.makeText(context, "Track saved on watch", Toast.LENGTH_SHORT).show()
                        }
                        is OfflineDownloadResult.Failed -> {
                            downloadState = DownloadState.NotDownloaded
                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                        OfflineDownloadResult.Skipped -> {
                            downloadState = DownloadState.Unavailable
                            Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                        }
                        OfflineDownloadResult.WaitingForNetwork -> {
                            downloadState = DownloadState.NotDownloaded
                            Toast.makeText(context, "Download queued · waiting for network", Toast.LENGTH_SHORT).show()
                        }
                    }
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

    BackHandler { (context as? Activity)?.finish() }

    if (isAmbient) {
        TidalPlayerAmbient(
            track = state.track,
            isPlaying = state.isPlaying,
            ambientOffset = ambientOffset,
            deviceHasLowBitAmbient = deviceHasLowBitAmbient,
            burnInProtectionRequired = burnInProtectionRequired,
            ambientTime = ambientTime,
        )
    } else {
        TidalPlayerNonAmbient(
            state = state,
            albumArt = albumArt,
            accent = accent,
            viewModel = viewModel,
            apiClient = apiClient,
            downloadState = downloadState,
            onDownload = ::startTrackDownload,
            onRemoveDownload = ::removeCurrentDownload,
            onOpenHome = onOpenHome,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            navigation = navigation,
            playlistOperationScope = navigation.viewModelScope,
        )
    }
}

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
    onOpenHome: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    navigation: PlayerNavigationState,
    playlistOperationScope: kotlinx.coroutines.CoroutineScope,
) {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val context = LocalContext.current
    val scale = playerLayoutScale()
    val density = LocalDensity.current
    var showVolume by remember { mutableStateOf(false) }
    var volumeChangePulse by remember { mutableIntStateOf(0) }
    var liked by navigation::liked
    var likeLoading by navigation::likeLoading
    var likeError by navigation::likeError
    val scope = rememberCoroutineScope()
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val loadedTrack = state.track
    val moreHintEligible = loadedTrack != null && state.error.isNullOrBlank()
    var showMoreHint by remember(loadedTrack?.id, moreHintEligible) {
        mutableStateOf(
            MoreActionsHintPolicy.shouldShow(
                hasTrack = moreHintEligible,
                hasBeenShown = MoreActionsHintPreference.hasBeenShown(context),
            ),
        )
    }
    val configuration = LocalConfiguration.current
    val actionsSheetTravelPx = with(density) { (configuration.screenHeightDp.dp - 18.dp).toPx().coerceAtLeast(1f) }

    fun markMoreHintLearned() {
        if (showMoreHint) showMoreHint = false
        MoreActionsHintPreference.markShown(context)
    }

    LaunchedEffect(loadedTrack?.id, navigation.showActionsSheet) {
        if (!navigation.showActionsSheet) runCatching { focusRequester.requestFocus() }
    }
    BackHandler(enabled = navigation.showActionsSheet) {
        navigation.closeActions()
    }
    LaunchedEffect(volumeChangePulse) {
        if (volumeChangePulse > 0) {
            delay(1_500)
            showVolume = false
        }
    }
    LaunchedEffect(showMoreHint) {
        if (showMoreHint) {
            delay(1_200)
            markMoreHintLearned()
        }
    }
    LaunchedEffect(state.track?.id) {
        val trackId = state.track?.id
        if (trackId != null && navigation.favoriteTrackId == trackId) return@LaunchedEffect
        likeError = null
        liked = false
        if (!trackId.isFavoriteLookupEligible()) {
            liked = false
            likeLoading = false
            return@LaunchedEffect
        }
        val validTrackId = trackId ?: return@LaunchedEffect
        likeLoading = true
        runCatching { apiClient.isFavoriteTrack(validTrackId) }
            .onSuccess { liked = it; navigation.favoriteTrackId = validTrackId }
            .onFailure { likeError = "Like unavailable" }
        likeLoading = false
    }

    navigation.addToPlaylistTrack?.let { trackSnapshot ->
        AddToPlaylistSheet(
            track = trackSnapshot,
            apiClient = apiClient,
            onBack = { navigation.addToPlaylistTrack = null },
            operationScope = playlistOperationScope,
            operationPending = navigation.pendingPlaylistTrackId != null,
            onOperationStarted = { trackId -> navigation.pendingPlaylistTrackId = trackId },
            onOperationResult = { message ->
                navigation.pendingPlaylistTrackId = null
                navigation.playlistResult = message.takeIf { it.isNotBlank() }
            },
        )
        return
    }

    if (navigation.showQueue) {
        QueueSheet(
            queue = state.queue,
            currentTrack = state.track,
            onJumpToIndex = { index ->
                viewModel.jumpToQueueIndex(index)
                navigation.showQueue = false
            },
            onBack = { navigation.showQueue = false },
        )
        return
    }

    ScreenScaffold(timeText = {}) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TidalColors.Black)
                .focusRequester(focusRequester)
                .onFocusChanged { if (BuildConfig.DEBUG) Log.d("UntidyRotaryVolume", "nowPlayingFocus=${it.isFocused} track=${loadedTrack?.id.orEmpty()}") }
                .onPreRotaryScrollEvent { event ->
                    if (navigation.showActionsSheet) return@onPreRotaryScrollEvent false
                    if (BuildConfig.DEBUG) Log.d("UntidyRotaryVolume", "rotary pixels=${event.verticalScrollPixels} volume=${state.volume}/${state.maxVolume}")
                    val maxVolume = state.maxVolume.coerceAtLeast(1)
                    val step = if (event.verticalScrollPixels > 0f) 1 else -1
                    val nextVolume = (state.volume + step).coerceIn(0, maxVolume)
                    if (nextVolume != state.volume) {
                        viewModel.setVolumeFraction(nextVolume.toFloat() / maxVolume.toFloat())
                        showVolume = true
                        volumeChangePulse += 1
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    true
                }
                .focusable(),
        ) {
            if (loadedTrack != null && state.error.isNullOrBlank() && !state.isLoading) {
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
                    enabled = !likeLoading && state.track?.id.isFavoriteLookupEligible(),
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
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val target = !liked
                        liked = target
                        likeLoading = true
                        likeError = null
                        scope.launch {
                            runCatching { apiClient.setFavoriteTrack(trackId, target) }
                                .onSuccess { persisted ->
                                    liked = persisted
                                    if (!view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                }
                                .onFailure {
                                    liked = !target
                                    likeError = "Like failed"
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
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
                    onClick = { navigation.showQueue = true },
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
                    text = loadedTrack.title,
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
                    text = loadedTrack.artist.ifBlank { loadedTrack.album.ifBlank { "Untidy" } },
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


                AnimatedVisibility(
                    visible = showMoreHint,
                    enter = fadeIn(),
                    exit = fadeOut(animationSpec = tween(300)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp * scale),
                ) {
                    Text(
                        text = "Swipe up for more",
                        color = TidalColors.OnSurfaceMuted,
                        fontSize = (10 * scale).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }

                BottomGripPullOverHandle(
                    scale = scale,
                    travelPx = actionsSheetTravelPx,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDragProgress = { dragPx ->
                        navigation.actionsSheetDragPx = dragPx.coerceIn(0f, actionsSheetTravelPx)
                    },
                    onArmedChange = { navigation.actionsGripArmed = it },
                    onRelease = { dragPx ->
                        val shouldOpen = dragPx > actionsSheetTravelPx * 0.28f
                        if (shouldOpen) markMoreHintLearned()
                        navigation.showActionsSheet = shouldOpen
                        navigation.actionsSheetDragPx = if (shouldOpen) actionsSheetTravelPx else 0f
                        navigation.actionsGripArmed = false
                    },
                    onAccessibilityOpen = {
                        markMoreHintLearned()
                        navigation.showActionsSheet = true
                    },
                )

                VolumeOverlay(
                    visible = showVolume,
                    volume = state.volume,
                    maxVolume = state.maxVolume,
                    accent = accent,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                )
            } else {
                PlayerEmptyState(
                    scale = scale,
                    accent = accent,
                    error = state.error,
                    loading = state.isLoading,
                    retryLabel = if (PlayerRecoveryPolicy.needsDownloadRepair(state.error)) "Repair" else "Retry",
                    onRetry = {
                        if (PlayerRecoveryPolicy.needsDownloadRepair(state.error) && loadedTrack != null) {
                            context.repairOfflineTrackDownload(loadedTrack)
                            Toast.makeText(context, "Repair queued. Check Downloads for progress.", Toast.LENGTH_LONG).show()
                            onOpenHome()
                        } else viewModel.retryPlayback()
                    },
                    onHome = onOpenHome,
                )
            }
            if (navigation.showActionsSheet || navigation.actionsSheetDragPx > 0f || navigation.actionsGripArmed) {
                ActionsPullOverOverlay(
                    progressPx = if (navigation.showActionsSheet) actionsSheetTravelPx else navigation.actionsSheetDragPx,
                    travelPx = actionsSheetTravelPx,
                    downloadState = downloadState,
                    deliveredAudioFormat = state.deliveredAudioFormat,
                    requestedOutputId = state.preferredAudioOutputDeviceId,
                    onClose = {
                        navigation.closeActions()
                    },
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onHome = onOpenHome,
                    onQueue = {
                        navigation.closeActions()
                        navigation.showQueue = true
                    },
                    onOutputSelected = { deviceId ->
                        viewModel.setPreferredAudioOutput(deviceId)
                    },
                    onAddToPlaylist = {
                        val track = state.track
                        when {
                            track == null -> "Nothing playing"
                            track.id.isBlank() || track.id == "tidal-current" || track.id.startsWith("fixture", ignoreCase = true) -> "Track unavailable"
                            !PlayerRecoveryPolicy.canStartPlaylistWrite(navigation.pendingPlaylistTrackId, track.id) -> "Adding to a playlist…"
                            else -> {
                                navigation.addToPlaylistTrack = track
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
private fun BottomGripPullOverHandle(
    scale: Float,
    travelPx: Float,
    modifier: Modifier = Modifier,
    onDragProgress: (Float) -> Unit,
    onArmedChange: (Boolean) -> Unit,
    onRelease: (Float) -> Unit,
    onAccessibilityOpen: () -> Unit,
) {
    val view = LocalView.current
    var draggedUpPx by remember { mutableStateOf(0f) }
    var hapticCommitted by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            // Keep the gesture zone beneath the transport targets. There is intentionally no tap
            // action here: opening More requires a deliberate upward pull from the bottom edge.
            .width(72.dp * scale)
            .height(18.dp * scale)
            .pointerInput(travelPx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        draggedUpPx = 0f
                        hapticCommitted = false
                        onArmedChange(true)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount >= 0f && draggedUpPx == 0f) return@detectVerticalDragGestures
                        change.consume()
                        draggedUpPx = (draggedUpPx - dragAmount).coerceIn(0f, travelPx)
                        onDragProgress(draggedUpPx)
                        if (!hapticCommitted && draggedUpPx >= travelPx * 0.10f) {
                            hapticCommitted = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                    onDragEnd = {
                        onRelease(draggedUpPx)
                        draggedUpPx = 0f
                    },
                    onDragCancel = {
                        onRelease(0f)
                        draggedUpPx = 0f
                    },
                )
            }
            .semantics {
                contentDescription = "More actions"
                onClick(label = "Open more actions") {
                    onAccessibilityOpen()
                    true
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 7.dp * scale)
                .size(width = 24.dp * scale, height = 3.dp * scale)
                .clip(CircleShape)
                .background(TidalColors.White.copy(alpha = 0.42f)),
        )
    }
}

@Composable
private fun ActionsPullOverOverlay(
    progressPx: Float,
    travelPx: Float,
    downloadState: DownloadState,
    deliveredAudioFormat: com.tidal.wear.core.playback.DeliveredAudioFormat?,
    requestedOutputId: Int?,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> String?,
    onHome: () -> Unit,
    onQueue: () -> Unit,
    onOutputSelected: (Int?) -> Unit,
    onAddToPlaylist: () -> String?,
    onViewAlbum: () -> String?,
    onViewArtist: () -> String?,
) {
    val fraction = (progressPx / travelPx).coerceIn(0f, 1f)
    val sheetOffsetPx = (travelPx - progressPx).coerceIn(0f, travelPx)
    val materialColor = TidalColors.Black
    val dimAlpha = 0.22f * fraction
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp)
                .graphicsLayer { translationY = sheetOffsetPx }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(materialColor),
        ) {
            ActionsSheet(
                downloadState = downloadState,
                deliveredAudioFormat = deliveredAudioFormat,
                requestedOutputId = requestedOutputId,
                onDownload = onDownload,
                onRemoveDownload = onRemoveDownload,
                onHome = onHome,
                onQueue = onQueue,
                onOutputSelected = onOutputSelected,
                onAddToPlaylist = onAddToPlaylist,
                onViewAlbum = onViewAlbum,
                onViewArtist = onViewArtist,
                onHandleClick = onClose,
            )
        }
    }
}

@Composable
private fun PlayerEmptyState(
    scale: Float,
    accent: Color,
    error: String?,
    loading: Boolean,
    retryLabel: String,
    onRetry: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (loading) "Starting playback…" else PlayerRecoveryPolicy.emptyMessage(error),
            color = if (error.isNullOrBlank()) TidalColors.OnSurfaceMuted else Color(0xFFFF8A80),
            fontSize = (13 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Return home to choose a track",
            color = TidalColors.OnSurfaceMuted,
            fontSize = (11 * scale).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp * scale),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp * scale)) {
            if (!error.isNullOrBlank()) {
                RecoveryButton(retryLabel, onRetry, scale)
            }
            RecoveryButton("Home", onHome, scale)
        }
    }
}

@Composable
private fun RecoveryButton(label: String, onClick: () -> Unit, scale: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(TidalColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp * scale, vertical = 12.dp * scale),
    ) {
        Text(label, color = TidalColors.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold)
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
    track == null || track.isDownloadEligible() != true -> DownloadState.Unavailable
    isOfflineTrackDownloaded(track.id) -> DownloadState.Downloaded
    else -> DownloadState.NotDownloaded
}

private fun TidalTrack.isDownloadEligible(): Boolean = id.isNotBlank() &&
    id != "tidal-current" &&
    !id.startsWith("fixture", ignoreCase = true)

private fun String?.isFavoriteLookupEligible(): Boolean =
    !isNullOrBlank() && this != "tidal-current" && !startsWith("fixture", ignoreCase = true)

@Composable
private fun TidalPlayerAmbient(
    track: TidalTrack?,
    isPlaying: Boolean,
    ambientOffset: Pair<Int, Int>,
    deviceHasLowBitAmbient: Boolean,
    burnInProtectionRequired: Boolean,
    ambientTime: LocalTime,
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
                text = ambientTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = if (deviceHasLowBitAmbient) 24.sp else 28.sp,
                fontWeight = FontWeight.Light,
                color = primary,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
internal fun rememberAudioOutputOptions(requestedDeviceId: Int?): List<AudioOutputOption> {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    var outputGeneration by remember(audioManager) { mutableIntStateOf(0) }
    DisposableEffect(audioManager) {
        if (audioManager == null) return@DisposableEffect onDispose {}
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                outputGeneration += 1
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                outputGeneration += 1
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return remember(audioManager, outputGeneration, requestedDeviceId) {
        audioManager.audioOutputOptions(requestedDeviceId)
    }
}

private fun AudioManager?.audioOutputOptions(requestedDeviceId: Int?): List<AudioOutputOption> {
    val audioManager = this ?: return emptyList()
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type.isWearOutputType() }
        .distinctBy { "${it.type}:${it.productName}" }
    val routedDeviceId = runCatching {
        audioManager.getAudioDevicesForAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        ).firstOrNull { routed -> devices.any { it.id == routed.id } }?.id
    }.getOrNull()
    val selectedId = AudioOutputSelectionPolicy.selectedDeviceId(
        requestedDeviceId = requestedDeviceId,
        routedDeviceId = routedDeviceId,
        availableDeviceIds = devices.mapTo(mutableSetOf()) { it.id },
    )
    return devices.map { device ->
        AudioOutputOption(
            id = device.id,
            label = device.outputLabel(),
            selected = device.id == selectedId,
        )
    }
}

private fun Int.isWearOutputType(): Boolean = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
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
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> "Bluetooth"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Watch speaker"
        else -> "Output"
    }
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> typeLabel
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> product?.takeUnless { it.equals(typeLabel, ignoreCase = true) } ?: typeLabel
        else -> typeLabel
    }
}

@Composable
private fun playerLayoutScale(): Float {
    val configuration = LocalConfiguration.current
    return (configuration.screenWidthDp / 192f).coerceIn(0.86f, 1.25f)
}



