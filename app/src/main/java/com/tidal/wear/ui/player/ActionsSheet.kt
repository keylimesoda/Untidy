package com.tidal.wear.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
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
    onQueue: () -> Unit,
    onOutputSettings: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onViewAlbum: () -> Unit,
    onViewArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var outputExpanded by remember { mutableStateOf(false) }
    val preferredOutput = outputOptions.firstOrNull { it.preferred } ?: outputOptions.firstOrNull()
    Column(
        modifier = modifier.fillMaxSize().background(TidalColors.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 24.dp, height = 3.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x4DFFFFFF)),
        )
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ActionRow(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Queue",
                    rightIndicator = null,
                    iconTint = TidalColors.Cyan,
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
                    onClick = onDownload,
                )
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
            item { ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", null, TidalColors.White, onClick = onAddToPlaylist) }
            item { ActionRow(Icons.Filled.Album, "View album", null, TidalColors.White, onClick = onViewAlbum) }
            item { ActionRow(Icons.Filled.Person, "View artist", null, TidalColors.White, onClick = onViewArtist) }
        }
    }
}

data class AudioOutputOption(
    val label: String,
    val preferred: Boolean,
)

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
