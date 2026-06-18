package com.tidal.wear.ui.recent

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.size.Size
import com.tidal.wear.BuildConfig
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.recent.RecentItem
import com.tidal.wear.recent.toTrack
import com.tidal.wear.ui.art.rememberDeferredRowArtwork
import com.tidal.wear.ui.components.RowArtworkThumb
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.player.AddToPlaylistSheet
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrackContextScreen(
    apiClient: TidalApiClient,
    initialRecent: RecentItem?,
    trackId: String,
    onPlayTrack: (TidalTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    var track by remember(trackId, initialRecent) { mutableStateOf(initialRecent?.toTrack() ?: placeholderTrack(trackId)) }
    var loading by remember(trackId) { mutableStateOf(true) }
    var showAddToPlaylist by remember(trackId) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(trackId) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { apiClient.track(trackId) } }
            .onSuccess { loaded -> if (loaded != null) track = track.mergeLoaded(loaded) }
            .onFailure { throwable -> if (throwable is CancellationException) throw throwable }
        loading = false
    }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            track = track,
            apiClient = apiClient,
            onBack = { showAddToPlaylist = false },
        )
        return
    }

    TrackContextContent(
        track = track,
        loading = loading,
        downloaded = context.isOfflineTrackDownloaded(track.id),
        onPlayTrack = { onPlayTrack(track) },
        onOpenAlbum = {
            track.albumId.takeIf { it.isNotBlank() }?.let(onOpenAlbum)
                ?: Toast.makeText(context, "Album unavailable", Toast.LENGTH_SHORT).show()
        },
        onOpenArtist = {
            track.artistId.takeIf { it.isNotBlank() }?.let(onOpenArtist)
                ?: Toast.makeText(context, "Artist unavailable", Toast.LENGTH_SHORT).show()
        },
        onAddToPlaylist = { showAddToPlaylist = true },
        onDownload = { context.startDebugDownload(track) },
    )
}

@Composable
private fun TrackContextContent(
    track: TidalTrack,
    loading: Boolean,
    downloaded: Boolean,
    onPlayTrack: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val artwork = rememberDeferredRowArtwork(
        artworkUrl = track.artworkUrl,
        requestSize = Size(128, 128),
        enabled = true,
        delayMillis = 0L,
    )
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = WearListPadding.RoundScreenCompact,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RowArtworkThumb(
                        label = track.title,
                        fallback = "♪",
                        bitmap = artwork,
                        accent = TidalColors.Cyan,
                    )
                    Text(
                        text = track.title.ifBlank { "Track" },
                        color = TidalColors.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { if (loading) "Loading details…" else "Track context" },
                        color = TidalColors.OnSurfaceMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
            item { ContextActionChip("Play", "Start this track", Icons.Filled.PlayArrow, TidalColors.Cyan, onPlayTrack) }
            item { ContextActionChip("View album", if (track.albumId.isBlank()) "Album unavailable" else track.album.ifBlank { "Open album" }, Icons.Filled.Album, TidalColors.White, onOpenAlbum) }
            item { ContextActionChip("View artist", if (track.artistId.isBlank()) "Artist unavailable" else track.artist.ifBlank { "Open artist" }, Icons.Filled.Person, TidalColors.White, onOpenArtist) }
            item { ContextActionChip("Add to playlist", "Choose a playlist", Icons.AutoMirrored.Filled.PlaylistAdd, TidalColors.White, onAddToPlaylist) }
            item {
                ContextActionChip(
                    label = when {
                        downloaded -> "Downloaded"
                        else -> "Download"
                    },
                    secondary = if (downloaded) "On watch" else "Offline unavailable in release",
                    icon = Icons.Filled.Download,
                    iconTint = if (downloaded) TidalColors.Cyan else TidalColors.White,
                    onClick = onDownload,
                )
            }
        }
    }
}

@Composable
private fun ContextActionChip(
    label: String,
    secondary: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TidalColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(secondary, color = TidalColors.OnSurfaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun placeholderTrack(trackId: String): TidalTrack = TidalTrack(
    id = trackId,
    title = "Track",
    artist = "",
    album = "",
)

private fun TidalTrack.mergeLoaded(loaded: TidalTrack): TidalTrack = loaded.copy(
    title = loaded.title.ifBlank { title },
    artist = loaded.artist.ifBlank { artist },
    album = loaded.album.ifBlank { album },
    artworkUrl = loaded.artworkUrl ?: artworkUrl,
    albumId = loaded.albumId.ifBlank { albumId },
    artistId = loaded.artistId.ifBlank { artistId },
)

private fun Context.startDebugDownload(track: TidalTrack) {
    when {
        !BuildConfig.DEBUG -> Toast.makeText(this, "Offline downloads are not available in this build", Toast.LENGTH_LONG).show()
        track.id.isBlank() -> Toast.makeText(this, "Track unavailable", Toast.LENGTH_SHORT).show()
        else -> runCatching {
            val intent = Intent().setClassName(packageName, "$packageName.debug.OfflineProofActivity")
                .putExtra("trackId", track.id)
                .putExtra("countryCode", "US")
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, "Offline download unavailable", Toast.LENGTH_SHORT).show()
        }
    }
}
