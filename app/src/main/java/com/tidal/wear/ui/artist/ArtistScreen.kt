package com.tidal.wear.ui.artist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import coil.size.Size
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.components.TidalResultChip
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtistScreen(
    apiClient: TidalApiClient,
    artistId: String,
    initialArtist: TidalArtist?,
    onPlayTrack: (TidalTrack) -> Unit,
    onPlayQueue: (List<TidalTrack>, Int) -> Unit,
    onOpenAlbum: (TidalAlbum) -> Unit,
    onOpenPlaylist: (TidalPlaylist) -> Unit,
    onOpenArtist: (TidalArtist) -> Unit,
) {
    var artist by remember(artistId, initialArtist) { mutableStateOf(initialArtist) }
    var result by remember(artistId) { mutableStateOf<TidalSearchResult?>(null) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId) {
        loading = true
        error = null
        try {
            val loadedArtist = withContext(Dispatchers.IO) { runCatching { apiClient.artist(artistId) }.getOrNull() }
            val loadedResult = withContext(Dispatchers.IO) { apiClient.artistContent(artistId) }
            artist = loadedArtist?.copy(
                name = loadedArtist.name.ifBlank { initialArtist?.name.orEmpty() },
                artworkUrl = loadedArtist.artworkUrl ?: initialArtist?.artworkUrl,
            ) ?: initialArtist
            result = loadedResult
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            result = null
            error = "Artist unavailable"
        } finally {
            loading = false
        }
    }

    val safeResult = result ?: TidalSearchResult()
    val title = artist?.name ?: "Artist"
    val artworkUrl = artist?.artworkUrl
    val art = rememberArtworkPalette(artworkUrl, Size(160, 160))
    val accent = art.palette.accentColor()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    LaunchedEffect(artistId, loading) {
        if (!loading) listState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ArtistHeader(
                    name = title,
                    bitmap = art.bitmap,
                    accent = accent,
                )
            }
            when {
                loading -> item { StatusText("Loading artist…") }
                error != null -> item { StatusText(error.orEmpty()) }
                safeResult.isEmpty() -> item { StatusText("No artist content found") }
                else -> {
                    section("Tracks", safeResult.tracks.take(12)) { track ->
                        TidalResultChip(
                            label = track.title,
                            secondaryLabel = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Track" },
                            artworkUrl = track.artworkUrl,
                            fallback = "♪",
                            onClick = {
                                if (safeResult.tracks.size > 1) {
                                    onPlayQueue(safeResult.tracks, safeResult.tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                                } else {
                                    onPlayTrack(track)
                                }
                            },
                        )
                    }
                    section("Albums", safeResult.albums.take(12)) { album ->
                        TidalResultChip(
                            label = album.title,
                            secondaryLabel = album.artist.ifBlank { title },
                            artworkUrl = album.artworkUrl,
                            fallback = "▣",
                            onClick = { onOpenAlbum(album) },
                        )
                    }
                    section("Playlists", safeResult.playlists.take(8)) { playlist ->
                        TidalResultChip(
                            label = playlist.title,
                            secondaryLabel = playlist.creator.ifBlank { "Playlist" },
                            artworkUrl = playlist.artworkUrl,
                            fallback = "≡",
                            onClick = { onOpenPlaylist(playlist) },
                        )
                    }
                    section("Related artists", safeResult.artists.take(8)) { relatedArtist ->
                        TidalResultChip(
                            label = relatedArtist.name,
                            secondaryLabel = "Artist",
                            artworkUrl = relatedArtist.artworkUrl,
                            fallback = "★",
                            onClick = { onOpenArtist(relatedArtist) },
                        )
                    }
                }
            }
        }
    }
}

private fun TidalSearchResult.isEmpty(): Boolean =
    tracks.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty()

private fun <T> androidx.wear.compose.material.ScalingLazyListScope.section(
    title: String,
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item { SectionHeader(title) }
    items.forEach { element -> item { itemContent(element) } }
}

@Composable
private fun ArtistHeader(
    name: String,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    accent: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .background(lerp(TidalColors.SurfaceHigh, accent, 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("★", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            }
        }
        Text(
            text = name,
            color = TidalColors.White,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TidalColors.Cyan,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp),
    )
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
