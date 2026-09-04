package com.tidal.wear.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors
import com.tidal.wear.core.playback.DeliveredAudioFormat

sealed interface DownloadState {
    data object Unavailable : DownloadState
    data object NotDownloaded : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Downloaded : DownloadState
}

@Composable
fun ActionsSheet(
    downloadState: DownloadState,
    deliveredAudioFormat: DeliveredAudioFormat?,
    requestedOutputId: Int?,
    onDownload: () -> Unit,
    onRemoveDownload: () -> String?,
    onHome: () -> Unit,
    onQueue: () -> Unit,
    onOutputSelected: (Int?) -> Unit,
    onAddToPlaylist: () -> String?,
    onViewAlbum: () -> String?,
    onViewArtist: () -> String?,
    modifier: Modifier = Modifier,
    forceHandleArmed: Boolean = false,
    onHandleClick: (() -> Unit)? = null,
) {
    var confirmRemoveDownload by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val outputOptions = rememberAudioOutputOptions(requestedOutputId)
    val quality = remember(deliveredAudioFormat) { deliveredAudioFormat.toQualityGemPresentation() }
    val listState = rememberTransformingLazyColumnState()
    fun runAction(action: () -> String?) {
        actionMessage = action()
    }
    Column(
        modifier = modifier.fillMaxSize().background(TidalColors.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(38.dp)
                .clickable(enabled = onHandleClick != null) { onHandleClick?.invoke() },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(width = 28.dp, height = 3.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(TidalColors.White.copy(alpha = if (forceHandleArmed) 0.76f else 0.42f)),
            )
        }
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp)
                .rotaryScrollableWithFocus(listState),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(56.dp)
                        .padding(bottom = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(TidalColors.Surface)
                            .clickable(onClick = onHome),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Untidy home",
                            modifier = Modifier.size(22.dp),
                            tint = TidalColors.Cyan,
                        )
                    }
                    if (quality != null) {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            QualityGem(quality)
                        }
                    }
                }
            }
            item {
                val downloadAvailable = downloadState != DownloadState.Unavailable
                ActionRow(
                    icon = Icons.Filled.Download,
                    label = downloadLabel(downloadState),
                    rightIndicator = if (downloadState == DownloadState.Downloaded) Icons.Filled.Check else null,
                    iconTint = when {
                        !downloadAvailable -> TidalColors.OnSurfaceMuted
                        downloadState != DownloadState.NotDownloaded -> TidalColors.Cyan
                        else -> TidalColors.White
                    },
                    enabled = downloadAvailable,
                    onClick = {
                        if (downloadState == DownloadState.Downloaded) {
                            confirmRemoveDownload = true
                            actionMessage = "Keeps it in TIDAL"
                        } else {
                            onDownload()
                        }
                    },
                )
            }
            if (confirmRemoveDownload) {
                item {
                    ActionRow(
                        icon = Icons.Filled.Delete,
                        label = "Remove download",
                        rightIndicator = null,
                        iconTint = TidalColors.Cyan,
                        onClick = {
                            actionMessage = onRemoveDownload()
                            confirmRemoveDownload = false
                        },
                    )
                }
                item {
                    ActionRow(
                        icon = Icons.Filled.Check,
                        label = "Cancel",
                        rightIndicator = null,
                        iconTint = TidalColors.OnSurfaceMuted,
                        onClick = {
                            confirmRemoveDownload = false
                            actionMessage = null
                        },
                    )
                }
            }
            item {
                ActionRow(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Queue",
                    rightIndicator = null,
                    iconTint = TidalColors.White,
                    onClick = onQueue,
                )
            }
            item {
                SectionLabel("Play on")
            }
            if (outputOptions.isEmpty()) {
                item {
                    ActionRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "No audio outputs",
                        rightIndicator = null,
                        iconTint = TidalColors.OnSurfaceMuted,
                        enabled = false,
                        onClick = {},
                    )
                }
            } else {
                outputOptions.forEach { output ->
                    item {
                        ActionRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = output.label,
                            rightIndicator = if (output.selected) Icons.Filled.Check else null,
                            iconTint = if (output.selected) TidalColors.Cyan else TidalColors.OnSurfaceMuted,
                            selected = output.selected,
                            onClick = {
                                onOutputSelected(output.id)
                            },
                        )
                    }
                }
            }
            item { ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", null, TidalColors.White, onClick = { runAction(onAddToPlaylist) }) }
            item { ActionRow(Icons.Filled.Album, "View album", null, TidalColors.White, onClick = { runAction(onViewAlbum) }) }
            item { ActionRow(Icons.Filled.Person, "View artist", null, TidalColors.White, onClick = { runAction(onViewArtist) }) }
            if (actionMessage != null) {
                item { ActionStatusLine(actionMessage.orEmpty()) }
            }
        }
    }
}

data class AudioOutputOption(
    val id: Int?,
    val label: String,
    /** The output Untidy has requested for its own player, not a claim about system routing. */
    val selected: Boolean,
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TidalColors.OnSurfaceMuted,
        style = MaterialTheme.typography.caption1,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(0.82f).padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun QualityGem(quality: QualityGemPresentation) {
    val tint = when (quality.tier) {
        DeliveredQualityTier.Low -> TidalColors.White
        DeliveredQualityTier.High -> TidalColors.Cyan
        DeliveredQualityTier.Max -> TidalColors.QualityMax
        DeliveredQualityTier.Unknown -> TidalColors.OnSurfaceMuted
    }
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .shadow(
                elevation = 7.dp,
                shape = shape,
                ambientColor = tint.copy(alpha = 0.24f),
                spotColor = tint.copy(alpha = 0.34f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(TidalColors.SurfaceHigh.copy(alpha = 0.94f), TidalColors.Black),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.32f), shape)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = quality.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionStatusLine(text: String) {
    Text(
        text = text,
        color = Color(0xFFFF8A80),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(0.86f).padding(vertical = 6.dp),
    )
}

private fun downloadLabel(state: DownloadState): String = when (state) {
    DownloadState.Unavailable -> "Download unavailable"
    DownloadState.NotDownloaded -> "Download"
    is DownloadState.Downloading -> "Downloading ${(state.progress * 100).toInt()}%"
    DownloadState.Downloaded -> "Downloaded"
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    rightIndicator: ImageVector?,
    iconTint: Color,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TidalColors.Cyan.copy(alpha = 0.20f) else TidalColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.caption1,
            fontSize = if (label.startsWith("Downloading")) 12.sp else 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (enabled) TidalColors.White else TidalColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (rightIndicator != null) {
                Icon(imageVector = rightIndicator, contentDescription = null, modifier = Modifier.size(20.dp), tint = TidalColors.Cyan)
            }
        }
    }
}
