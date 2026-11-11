package com.tidal.wear.ui.album

import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Icon
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import coil.size.Size
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.offline.OfflineUnit
import com.tidal.wear.core.playback.offline.collectionDownloadSummary
import com.tidal.wear.core.playback.offline.OfflineTrackDownloader
import com.tidal.wear.core.playback.offline.offlineUnitPresentation
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.components.RetryStatusChip
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AlbumScreen(
    apiClient: TidalApiClient,
    offlineDownloader: OfflineTrackDownloader,
    albumId: String,
    initialAlbum: TidalAlbum?,
    onPlayAlbum: (TidalAlbum, List<TidalTrack>, Int) -> Unit,
) {
    var album by remember(albumId, initialAlbum) { mutableStateOf(initialAlbum) }
    var tracks by remember(albumId) { mutableStateOf(emptyList<TidalTrack>()) }
    var loading by remember(albumId) { mutableStateOf(true) }
    var error by remember(albumId) { mutableStateOf<String?>(null) }
    var retryTick by remember(albumId) { mutableIntStateOf(0) }
    var liked by remember(albumId) { mutableStateOf(false) }
    var likeLoading by remember(albumId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

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
    val collectionDownloadSummary by remember(offlineDownloader, tracks) {
        offlineDownloader.observeCollectionSummary(tracks)
    }.collectAsState(initial = context.collectionDownloadSummary(tracks))
    val offlinePresentation = remember(collectionDownloadSummary) {
        collectionDownloadSummary.offlineUnitPresentation(OfflineUnit.Album, downloadsAvailable = true)
    }
    val art = rememberArtworkPalette(artworkUrl, Size(160, 160))
    val accent = art.palette.accentColor()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    LaunchedEffect(albumId) {
        if (albumId.isBlank()) return@LaunchedEffect
        likeLoading = true
        runCatching { withContext(Dispatchers.IO) { apiClient.isFavoriteAlbum(albumId) } }
            .onSuccess { liked = it }
            .onFailure { Log.d("Untidy/API", "favorite album state unavailable") }
        likeLoading = false
    }

    fun recentAlbum(): TidalAlbum = album ?: TidalAlbum(
        id = albumId,
        title = title,
        artist = tracks.firstOrNull()?.artist.orEmpty(),
        artworkUrl = artworkUrl,
        trackCount = tracks.size,
    )

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
                    liked = liked,
                    likeLoading = likeLoading,
                    onLike = {
                        if (likeLoading || albumId.isBlank()) return@AlbumArtworkHero
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val target = !liked
                        liked = target
                        likeLoading = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { apiClient.setFavoriteAlbum(albumId, target) } }
                                .onSuccess { persisted ->
                                    liked = persisted
                                    if (!view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                }
                                .onFailure {
                                    liked = !target
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    Toast.makeText(context, "Couldn't update album like", Toast.LENGTH_SHORT).show()
                                }
                            likeLoading = false
                        }
                    },
                )
            }
            if (tracks.isNotEmpty()) {
                item {
                    AlbumPlaybackActions(
                        accent = accent,
                        onPlay = { onPlayAlbum(recentAlbum(), tracks, 0) },
                        onShuffle = { onPlayAlbum(recentAlbum(), tracks.shuffled(), 0) },
                    )
                }
                item {
                    CollectionDownloadChip(
                        label = offlinePresentation.label,
                        secondary = offlinePresentation.detail,
                        accent = accent,
                        onClick = {
                            // BATT-H4: enqueue suspends off the UI thread for its DataStore read.
                            scope.launch {
                                val result = offlineDownloader.enqueueTracks(tracks)
                                Toast.makeText(
                                    context,
                                    if (result.scheduled > 0) "Album download queued" else "Already saved on watch",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
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
                            onClick = { onPlayAlbum(recentAlbum(), tracks, index) },
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
    liked: Boolean,
    likeLoading: Boolean,
    onLike: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AlbumLikeButton(
            liked = liked,
            enabled = !likeLoading,
            description = when {
                likeLoading -> "Loading album like state"
                liked -> "Unlike album"
                else -> "Like album"
            },
            onClick = onLike,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp),
        )
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
private fun AlbumPlaybackActions(
    accent: Color,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        PlaybackActionButton(
            label = "Play",
            accent = accent,
            onClick = onPlay,
            modifier = Modifier.weight(1f),
        )
        PlaybackActionButton(
            label = "Shuffle",
            accent = accent,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlbumLikeButton(
    liked: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (liked) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "albumLikeScale",
    )
    val tint by animateColorAsState(
        targetValue = if (liked) TidalColors.Cyan else TidalColors.White,
        animationSpec = tween(200),
        label = "albumLikeTint",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = liked,
            animationSpec = tween(150),
            label = "albumLikeIcon",
        ) { isLiked ->
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = description,
                tint = if (enabled) tint else TidalColors.OnSurfaceMuted,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
    }
}

@Composable
private fun PlaybackActionButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(lerp(TidalColors.SurfaceHigh, accent, 0.28f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(label, color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
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
            .heightIn(min = 48.dp)
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
