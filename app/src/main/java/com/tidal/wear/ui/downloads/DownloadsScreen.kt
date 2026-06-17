package com.tidal.wear.ui.downloads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.components.TidalResultChip
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun DownloadsScreen(
    context: Context,
    onPlayTrack: (TidalTrack) -> Unit,
) {
    var reloadTick by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(emptyList<DownloadedTrackRow>()) }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    LaunchedEffect(reloadTick) {
        tracks = context.readDownloadedTrackRows()
        listState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = WearListPadding.RoundScreenCompact,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { DownloadsTitle() }
            item {
                DownloadsSummaryChip(
                    count = tracks.size,
                    onClick = { reloadTick++ },
                )
            }
            if (tracks.isEmpty()) {
                item { EmptyDownloadsChip() }
            } else {
                tracks.forEach { row ->
                    item {
                        TidalResultChip(
                            label = row.title,
                            secondaryLabel = row.artist.ifBlank { "Downloaded" },
                            artworkUrl = null,
                            fallback = "↓",
                            onClick = { onPlayTrack(row.toTrack()) },
                        )
                    }
                }
            }
        }
    }
}

private data class DownloadedTrackRow(
    val id: String,
    val title: String,
    val artist: String,
    val downloadedAt: Long,
) {
    fun toTrack(): TidalTrack = TidalTrack(id = id, title = title, artist = artist, album = "Downloaded")
}

private fun Context.readDownloadedTrackRows(): List<DownloadedTrackRow> {
    val prefs = getSharedPreferences("offline-downloads", Context.MODE_PRIVATE)
    return prefs.all.keys
        .filter { it.startsWith("downloaded:") && prefs.getBoolean(it, false) }
        .mapNotNull { key ->
            val id = key.removePrefix("downloaded:").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DownloadedTrackRow(
                id = id,
                title = prefs.getString("title:$id", null)?.takeIf { it.isNotBlank() } ?: "Downloaded track",
                artist = prefs.getString("artist:$id", null).orEmpty(),
                downloadedAt = prefs.getLong("downloadedAt:$id", 0L),
            )
        }
        .sortedByDescending { it.downloadedAt }
}

@Composable
private fun DownloadsTitle() {
    Text(
        text = "Downloads",
        color = TidalColors.White,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun DownloadsSummaryChip(count: Int, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text("Tracks") },
        secondaryLabel = { Text(if (count == 1) "1 downloaded" else "$count downloaded") },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.SurfaceHigh,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
        ),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun EmptyDownloadsChip() {
    Chip(
        onClick = {},
        enabled = false,
        label = { Text("No downloads yet") },
        secondaryLabel = { Text("Download from Now Playing") },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
        ),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
