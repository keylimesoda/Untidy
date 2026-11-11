package com.tidal.wear.ui.downloads

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tidal.wear.core.playback.offline.DownloadedTrackSummary
import com.tidal.wear.core.playback.offline.OfflineDownloadScheduler
import com.tidal.wear.core.playback.offline.OfflineQueuedTrack
import com.tidal.wear.core.playback.offline.readOfflineDownloadedTracks
import com.tidal.wear.core.playback.offline.removeOfflineTrackDownload
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloadRepairRequired
import com.tidal.wear.core.playback.offline.repairOfflineTrackDownload
import com.tidal.wear.ui.components.TidalResultChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun DownloadsScreen(
    context: Context,
    onPlayTrack: (TidalTrack) -> Unit,
) {
    var confirmRemove by remember { mutableStateOf<DownloadedTrackSummary?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val scheduler = remember(context) { OfflineDownloadScheduler(context.applicationContext) }
    val activeTracks by remember(scheduler) { scheduler.observeActiveTracks() }.collectAsState(initial = emptyList())
    // WorkManager emits progress repeatedly while a download runs. Only re-scan the downloaded
    // track prefs when the queued set changes (start, completion, cancellation).
    val activeDownloadKey = remember(activeTracks) { activeTracks.map { it.id }.toSet() }
    var tracks by remember(activeDownloadKey) { mutableStateOf(context.readOfflineDownloadedTracks()) }
    var repairFlags by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    LaunchedEffect(tracks, activeDownloadKey) {
        repairFlags = withContext(Dispatchers.IO) {
            tracks.associate { row -> row.id to context.isOfflineTrackDownloadRepairRequired(row.id) }
        }
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
                    activeCount = activeTracks.size,
                )
            }
            activeTracks.forEach { row ->
                item {
                    ActiveDownloadChip(
                        row = row,
                        onCancel = {
                            context.removeOfflineTrackDownload(row.id)
                            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            if (tracks.isEmpty()) {
                item { EmptyDownloadsChip() }
            } else {
                tracks.forEach { row ->
                    val needsRepair = repairFlags[row.id] == true
                    item {
                        TidalResultChip(
                            label = row.title,
                            secondaryLabel = if (needsRepair) "Download needs repair" else row.artist.ifBlank { "Downloaded" },
                            artworkUrl = null,
                            fallback = "↓",
                            onClick = { onPlayTrack(row.toTrack()) },
                        )
                    }
                    if (needsRepair && activeTracks.none { it.id == row.id }) {
                        item {
                            Chip(
                                onClick = {
                                    scope.launch {
                                        context.repairOfflineTrackDownload(row.toTrack())
                                        Toast.makeText(context, "Repair queued", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Repair download") },
                                secondaryLabel = { Text("Keeps existing cached audio") },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                    item {
                        Chip(
                            onClick = { confirmRemove = row },
                            label = { Text("Remove download") },
                            secondaryLabel = { Text("Keeps it in TIDAL") },
                            colors = ChipDefaults.secondaryChipColors(
                                backgroundColor = TidalColors.Surface,
                                contentColor = TidalColors.White,
                                secondaryContentColor = TidalColors.OnSurfaceMuted,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
            confirmRemove?.let { row ->
                item {
                    Chip(
                        onClick = {
                            if (context.removeOfflineTrackDownload(row.id)) {
                                tracks = tracks.filterNot { it.id == row.id }
                                Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                                confirmRemove = null
                            } else {
                                Toast.makeText(context, "Couldn't remove download", Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Remove local copy?") },
                        secondaryLabel = { Text("Remove download") },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = TidalColors.Cyan,
                            contentColor = TidalColors.Black,
                            secondaryContentColor = TidalColors.Black.copy(alpha = 0.72f),
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                item {
                    Chip(
                        onClick = { confirmRemove = null },
                        label = { Text("Cancel") },
                        secondaryLabel = { Text("Keeps local copy") },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = TidalColors.SurfaceHigh,
                            contentColor = TidalColors.White,
                            secondaryContentColor = TidalColors.OnSurfaceMuted,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
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
private fun DownloadsSummaryChip(count: Int, activeCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(TidalColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            Text("Tracks", color = TidalColors.White, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append(if (count == 1) "1 downloaded" else "$count downloaded")
                    if (activeCount > 0) append(" · $activeCount saving")
                },
                color = TidalColors.OnSurfaceMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ActiveDownloadChip(row: OfflineQueuedTrack, onCancel: () -> Unit) {
    Chip(
        onClick = onCancel,
        label = { Text(row.title) },
        secondaryLabel = { Text("Saves on Wi-Fi · tap to cancel") },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
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
        label = { Text("No downloads on this watch") },
        secondaryLabel = { Text("Save albums or playlists to listen offline") },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
        ),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
