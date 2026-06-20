package com.tidal.wear.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors

sealed interface DownloadState {
    data object Unavailable : DownloadState
    data object NotDownloaded : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Downloaded : DownloadState
}

@Composable
fun ActionsSheet(
    downloadState: DownloadState,
    outputOptions: List<AudioOutputOption>,
    onDownload: () -> Unit,
    onRemoveDownload: () -> String?,
    onHome: () -> Unit,
    onQueue: () -> Unit,
    onOutputSettings: () -> Unit,
    onAddToPlaylist: () -> String?,
    onViewAlbum: () -> String?,
    onViewArtist: () -> String?,
    modifier: Modifier = Modifier,
    forceHandleArmed: Boolean = false,
    onHandleDragStart: (() -> Unit)? = null,
    onHandleDrag: ((Float) -> Unit)? = null,
    onHandleDragEnd: ((Float) -> Unit)? = null,
) {
    var outputExpanded by remember { mutableStateOf(false) }
    var confirmRemoveDownload by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val preferredOutput = outputOptions.firstOrNull { it.preferred } ?: outputOptions.firstOrNull()
    val listState = rememberTransformingLazyColumnState()
    val view = LocalView.current
    var handleArmed by remember { mutableStateOf(false) }
    var handleDragPx by remember { mutableStateOf(0f) }
    fun runAction(action: () -> String?) {
        actionMessage = action()
    }
    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF171717)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(28.dp)
                .pointerInput(onHandleDragStart, onHandleDrag, onHandleDragEnd) {
                    if (onHandleDragStart == null || onHandleDrag == null || onHandleDragEnd == null) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            handleArmed = true
                            handleDragPx = 0f
                            onHandleDragStart()
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        },
                        onDragCancel = {
                            handleArmed = false
                            handleDragPx = 0f
                            onHandleDragEnd(0f)
                        },
                        onDragEnd = {
                            handleArmed = false
                            onHandleDragEnd(handleDragPx)
                            handleDragPx = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            handleDragPx = (handleDragPx + dragAmount.y).coerceAtLeast(0f)
                            onHandleDrag(handleDragPx)
                        },
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = (if (handleArmed || forceHandleArmed) 78 else 34).dp, height = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color(0xFF8A8A8A).copy(alpha = if (handleArmed || forceHandleArmed) 1f else 0.72f)),
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
                ActionRow(
                    icon = Icons.Filled.Home,
                    label = "Home",
                    rightIndicator = null,
                    iconTint = TidalColors.Cyan,
                    onClick = onHome,
                )
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
                    icon = Icons.Filled.Settings,
                    label = "Output: ${preferredOutput?.label ?: "System"}",
                    rightIndicator = null,
                    iconTint = TidalColors.White,
                    onClick = { outputExpanded = !outputExpanded },
                )
            }
            if (outputExpanded) {
                outputOptions.forEach { output ->
                    item {
                        ActionRow(
                            icon = Icons.Filled.Settings,
                            label = output.label,
                            rightIndicator = if (output.preferred) Icons.Filled.Check else null,
                            iconTint = if (output.preferred) TidalColors.Cyan else TidalColors.OnSurfaceMuted,
                            onClick = onOutputSettings,
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
    val label: String,
    val preferred: Boolean,
)

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
    DownloadState.Unavailable -> "Offline unavailable"
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = if (label.startsWith("Downloading")) 12.sp else 14.sp,
            fontWeight = FontWeight.Normal,
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
