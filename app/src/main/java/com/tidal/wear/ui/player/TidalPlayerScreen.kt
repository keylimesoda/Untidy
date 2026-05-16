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
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.playback.NowPlayingUiState
import com.tidal.wear.playback.NowPlayingViewModel
import com.tidal.wear.ui.art.AlbumArt
import com.tidal.wear.ui.art.rememberAlbumArt
import com.tidal.wear.ui.components.AlbumArtCard
import com.tidal.wear.ui.components.CircularPerimeterProgress
import com.tidal.wear.ui.components.VolumeOverlay
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TidalPlayerScreen(
    viewModel: NowPlayingViewModel,
    isAmbient: Boolean,
    ambientOffset: Pair<Int, Int>,
) {
    val state by viewModel.state.collectAsState()
    val albumArt = rememberAlbumArt(state.track)
    val accent = if (albumArt?.bitmap != null) albumArt.palette.accentColor() else TidalColors.Cyan
    val context = LocalContext.current
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.NotDownloaded) }

    fun cycleDownloadState() {
        downloadState = when (downloadState) {
            DownloadState.NotDownloaded -> DownloadState.Downloading(0.47f)
            is DownloadState.Downloading -> DownloadState.Downloaded
            DownloadState.Downloaded -> DownloadState.NotDownloaded
        }
    }

    BackHandler { (context as? Activity)?.finish() }

    if (isAmbient) {
        TidalPlayerAmbient(state.track, albumArt, ambientOffset)
    } else {
        TidalPlayerNonAmbient(
            state = state,
            albumArt = albumArt,
            accent = accent,
            viewModel = viewModel,
            downloadState = downloadState,
            onDownload = ::cycleDownloadState,
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
    downloadState: DownloadState,
    onDownload: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val context = LocalContext.current
    val scale = playerLayoutScale()
    val density = LocalDensity.current
    var showVolume by remember { mutableStateOf(false) }
    var volumeChangePulse by remember { mutableIntStateOf(0) }
    var liked by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(volumeChangePulse) {
        if (volumeChangePulse > 0) {
            delay(1_500)
            showVolume = false
        }
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
            description = if (liked) "Unlike" else "Like",
            x = 10,
            y = 36,
            scale = scale,
            onClick = {
                if (!view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                liked = !liked
            },
        )
        SecondaryIconAt(
            icon = Icons.Filled.QueueMusic,
            description = "Queue",
            x = 134,
            y = 36,
            scale = scale,
            onClick = { Toast.makeText(context, "Queue coming soon", Toast.LENGTH_SHORT).show() },
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-11).dp * scale)
                .size(width = 24.dp * scale, height = 3.dp * scale)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x4DFFFFFF)),
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
                    onOutputSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }.onFailure {
                            Toast.makeText(context, "Open Wear OS Bluetooth settings to change output", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddToPlaylist = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                    onViewAlbum = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                    onViewArtist = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                )
            }
        }
    }
}

@Composable
private fun LikeIconAt(
    liked: Boolean,
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
            .clickable(onClick = onClick),
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
                tint = tint,
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

@Composable
private fun TidalPlayerAmbient(
    track: TidalTrack?,
    albumArt: AlbumArt?,
    ambientOffset: Pair<Int, Int>,
) {
    val scale = playerLayoutScale()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TidalColors.Black)
            .graphicsLayer {
                translationX = ambientOffset.first.toFloat()
                translationY = ambientOffset.second.toFloat()
            },
    ) {
        CircularPerimeterProgress(
            progress = 1f,
            modifier = Modifier.fillMaxSize().padding(3.dp * scale),
            trackColor = TidalColors.White.copy(alpha = 0.30f),
            progressColor = TidalColors.White.copy(alpha = 0.30f),
            strokeWidth = 2.dp * scale,
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 34.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumArtCard(bitmap = albumArt?.bitmap, accent = TidalColors.White, sizeDp = (64 * scale).roundToInt())
            Text(
                text = track?.title ?: "Untidy",
                style = MaterialTheme.typography.caption1,
                color = TidalColors.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(132.dp * scale).padding(top = 10.dp),
            )
            Box(Modifier.height(42.dp * scale))
            Text(
                text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 32.sp,
                fontWeight = FontWeight.W100,
                color = TidalColors.White,
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



