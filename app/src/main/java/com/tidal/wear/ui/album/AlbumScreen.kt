package com.tidal.wear.ui.album

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
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
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.OfflineUnit
import com.tidal.wear.core.playback.offline.collectionDownloadSummary
import com.tidal.wear.core.playback.offline.offlineUnitPresentation
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.components.RetryStatusChip
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumScreen(
    apiClient: TidalApiClient,
    albumId: String,
    initialAlbum: TidalAlbum?,
    onPlayAlbum: (TidalAlbum, List<TidalTrack>) -> Unit,
    onPlayQueue: (List<TidalTrack>, Int) -> Unit,
) {
    var album by remember(albumId, initialAlbum) { mutableStateOf(initialAlbum) }
    var tracks by remember(albumId) { mutableStateOf(emptyList<TidalTrack>()) }
    var loading by remember(albumId) { mutableStateOf(true) }
    var error by remember(albumId) { mutableStateOf<String?>(null) }
    var retryTick by remember(albumId) { mutableIntStateOf(0) }

    LaunchedEffect(albumId, retryTick) {
        loading = true
        error = null
        try {
            val loadedAlbum = withContext(Dispatchers.IO) { runCatching { apiClient.album(albumId) }.getOrNull() }
            val displayAlbum = loadedAlbum?.copy(
                artist = loadedAlbum.artist.ifBlank { initialAlbum?.artist.orEmpty() },
                artworkUrl = loadedAlbum.artworkUrl ?: initialAlbum?.artworkUrl,
            ) ?: initialAlbum
            val loadedTracks = withContext(Dispatchers.IO) { apiClient.albumTracks(albumId) }
            val fallbackArtwork = loadedTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                ?: displayAlbum?.artworkUrl
            val fallbackArtist = displayAlbum?.artist?.takeIf { it.isNotBlank() }
                ?: loadedTracks.firstOrNull { it.artist.isNotBlank() }?.artist
                ?: ""
            val fallbackTitle = displayAlbum?.title.orEmpty()
            album = displayAlbum?.copy(
                artist = displayAlbum.artist.ifBlank { fallbackArtist },
                artworkUrl = fallbackArtwork,
            )
            tracks = loadedTracks.map { track ->
                track.copy(
                    artist = track.artist.ifBlank { fallbackArtist.ifBlank { fallbackTitle } },
                    album = track.album.ifBlank { fallbackTitle },
                    artworkUrl = track.artworkUrl ?: fallbackArtwork,
                )
            }
            if (fallbackArtwork.isNullOrBlank() || fallbackArtist.isBlank()) {
                Log.d("Untidy/API", "album metadata incomplete art=${!fallbackArtwork.isNullOrBlank()} artist=${fallbackArtist.isNotBlank()} tracks=${loadedTracks.size}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            error = "Album unavailable"
            tracks = emptyList()
        } finally {
            loading = false
        }
    }

    val fallbackTitle = tracks.firstOrNull()?.album?.takeIf { it.isNotBlank() } ?: "Album"
    val title = album?.title ?: fallbackTitle
    val artworkUrl = album?.artworkUrl ?: tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
    val context = androidx.compose.ui.platform.LocalContext.current
    val collectionDownloadSummary = remember(tracks) { context.collectionDownloadSummary(tracks) }
    val offlinePresentation = remember(collectionDownloadSummary) {
        collectionDownloadSummary.offlineUnitPresentation(OfflineUnit.Album)
    }
    val art = rememberArtworkPalette(artworkUrl, Size(160, 160))
    val accent = art.palette.accentColor()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    LaunchedEffect(albumId, loading) {
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
                AlbumArtworkHero(
                    title = title,
                    bitmap = art.bitmap,
                    accent = accent,
                )
            }
            if (tracks.isNotEmpty()) {
                item {
                    PlayAlbumChip(
                        accent = accent,
                        onClick = {
                            val recentAlbum = album ?: TidalAlbum(
                                id = albumId,
                                title = title,
                                artist = tracks.firstOrNull()?.artist.orEmpty(),
                                artworkUrl = artworkUrl,
                                trackCount = tracks.size,
                            )
                            onPlayAlbum(recentAlbum, tracks)
                        },
                    )
                }
                item {
                    CollectionDownloadChip(
                        label = offlinePresentation.label,
                        secondary = offlinePresentation.detail,
                        accent = accent,
                        onClick = {
                            Toast.makeText(
                                context,
                                offlinePresentation.message,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
            when {
                loading -> item { StatusText("Loading tracks…") }
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
private fun AlbumArtworkHero(
    title: String,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(lerp(TidalColors.SurfaceHigh, accent, 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("▣", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            }
        }
    }
}

@Composable
private fun PlayAlbumChip(
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
        Text("Play Album", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
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
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
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
        Text(
            track.title,
            color = TidalColors.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
