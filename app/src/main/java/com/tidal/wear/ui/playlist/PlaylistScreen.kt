package com.tidal.wear.ui.playlist

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import coil.size.Size
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.collectionDownloadSummary
import com.tidal.wear.core.playback.offline.hasFailures
import com.tidal.wear.core.playback.offline.isFullyDownloaded
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.components.PlaylistArtwork
import com.tidal.wear.ui.components.RetryStatusChip
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlaylistScreen(
    apiClient: TidalApiClient,
    playlistId: String,
    initialPlaylist: TidalPlaylist?,
    onPlayQueue: (List<TidalTrack>, Int) -> Unit,
) {
    var playlist by remember(playlistId, initialPlaylist) { mutableStateOf(initialPlaylist) }
    var tracks by remember(playlistId) { mutableStateOf(emptyList<TidalTrack>()) }
    var loading by remember(playlistId) { mutableStateOf(true) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }
    var retryTick by remember(playlistId) { mutableIntStateOf(0) }

    LaunchedEffect(playlistId, retryTick) {
        loading = true
        error = null
        try {
            val loadedPlaylist = withContext(Dispatchers.IO) { runCatching { apiClient.playlist(playlistId) }.getOrNull() }
            val displayPlaylist = loadedPlaylist?.copy(
                creator = loadedPlaylist.creator.ifBlank { initialPlaylist?.creator.orEmpty() },
                artworkUrl = loadedPlaylist.artworkUrl ?: initialPlaylist?.artworkUrl,
            ) ?: initialPlaylist
            val loadedTracks = withContext(Dispatchers.IO) { apiClient.playlistTracks(playlistId) }
            val fallbackArtwork = displayPlaylist?.artworkUrl
            val fallbackTitle = displayPlaylist?.title.orEmpty()
            val fallbackCreator = displayPlaylist?.creator.orEmpty()
            playlist = displayPlaylist
            tracks = loadedTracks.map { track ->
                track.copy(
                    artist = track.artist.ifBlank { fallbackCreator },
                    album = track.album.ifBlank { fallbackTitle },
                    artworkUrl = track.artworkUrl ?: fallbackArtwork,
                )
            }
            if (fallbackArtwork.isNullOrBlank() || fallbackCreator.isBlank()) {
                val hasTrackArtwork = loadedTracks.any { !it.artworkUrl.isNullOrBlank() }
                Log.d("Untidy/API", "playlist metadata incomplete art=${!fallbackArtwork.isNullOrBlank()} trackArt=$hasTrackArtwork creator=${fallbackCreator.isNotBlank()} tracks=${loadedTracks.size}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            error = "Playlist unavailable"
            tracks = emptyList()
        } finally {
            loading = false
        }
    }

    val title = playlist?.title ?: "Playlist"
    val creator = playlist?.creator.orEmpty()
    val playlistArtworkUrl = playlist?.artworkUrl
    val trackArtworkUrls = remember(tracks) {
        tracks.mapNotNull { it.artworkUrl?.takeIf(String::isNotBlank) }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val collectionDownloadSummary = remember(tracks) { context.collectionDownloadSummary(tracks) }
    val art = rememberArtworkPalette(playlistArtworkUrl ?: trackArtworkUrls.firstOrNull(), Size(160, 160))
    val accent = art.palette.accentColor()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    LaunchedEffect(playlistId, loading) {
        if (!loading) listState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = WearListPadding.RoundScreen,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                PlaylistHeader(
                    title = title,
                    creator = creator,
                    artworkUrl = playlistArtworkUrl,
                    trackArtworkUrls = trackArtworkUrls,
                    artworkBitmap = if (!playlistArtworkUrl.isNullOrBlank()) art.bitmap else null,
                    accent = accent,
                )
            }
            if (tracks.isNotEmpty()) {
                item {
                    PlayAllChip(
                        accent = accent,
                        onClick = { onPlayQueue(tracks, 0) },
                    )
                }
                item {
                    CollectionDownloadChip(
                        label = collectionDownloadSummary.actionLabel("Download playlist"),
                        secondary = collectionDownloadSummary.detailLabel("Playlist"),
                        accent = accent,
                        onClick = {
                            Toast.makeText(
                                context,
                                collectionDownloadSummary.toastLabel("Playlist download"),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
            when {
                loading -> item { StatusText("Loading playlist…") }
                error != null -> item { RetryStatusChip(title = error.orEmpty(), onClick = { retryTick += 1 }) }
                tracks.isEmpty() -> item { StatusText("No tracks found") }
                else -> tracks.forEachIndexed { index, track ->
                    item {
                        TrackChip(
                            index = index + 1,
                            track = track,
                            accent = accent,
                            onClick = { onPlayQueue(tracks, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    creator: String,
    artworkUrl: String?,
    trackArtworkUrls: List<String>,
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    accent: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaylistArtwork(
            artworkUrl = artworkUrl,
            trackArtworkUrls = trackArtworkUrls,
            accent = accent,
            contentDescription = title,
            artworkBitmap = artworkBitmap,
        )
        Text(
            text = title,
            color = TidalColors.White,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        )
        if (creator.isNotBlank()) {
            Text(
                text = creator,
                color = TidalColors.OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            )
        }
    }
}

@Composable
private fun PlayAllChip(
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(lerp(TidalColors.SurfaceHigh, accent, 0.28f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Play all", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun CollectionDownloadChip(
    label: String,
    secondary: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TidalColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = "$label. $secondary" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "↓",
            color = accent,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TidalColors.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondary,
                color = TidalColors.OnSurfaceMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun com.tidal.wear.core.playback.offline.CollectionDownloadSummary.actionLabel(defaultLabel: String): String = when {
    playableCount <= 0 -> "Offline unavailable"
    hasFailures() -> "$failedCount failed"
    downloadedCount <= 0 -> defaultLabel
    isFullyDownloaded() -> "Downloaded $downloadedCount/$playableCount"
    else -> "Partial $downloadedCount/$playableCount"
}

private fun com.tidal.wear.core.playback.offline.CollectionDownloadSummary.detailLabel(kind: String): String = when {
    playableCount <= 0 -> "$kind has no playable tracks"
    hasFailures() && downloadedCount > 0 -> "Partial $downloadedCount/$playableCount · tap to retry later"
    hasFailures() -> "Tap to retry later"
    downloadedCount <= 0 -> "Tracks save one at a time for now"
    isFullyDownloaded() -> "All playable tracks on watch"
    else -> "Local-valid subset plays offline"
}

private fun com.tidal.wear.core.playback.offline.CollectionDownloadSummary.toastLabel(defaultPrefix: String): String = when {
    playableCount <= 0 -> "Offline unavailable"
    hasFailures() -> "Failed tracks can retry in downloads"
    downloadedCount <= 0 -> "$defaultPrefix is coming after track MVP"
    isFullyDownloaded() -> "Downloaded tracks play offline"
    else -> "Partial download plays offline subset"
}

@Composable
private fun TrackChip(
    index: Int,
    track: TidalTrack,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 18.dp, vertical = 1.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${track.title}" }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            color = accent,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = TidalColors.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artist.isNotBlank()) {
                Text(
                    track.artist,
                    color = TidalColors.OnSurfaceMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = TidalColors.OnSurfaceMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
