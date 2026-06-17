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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import coil.size.Size
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.ReleaseVersionPreference
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtistAlbums
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.components.TidalResultChip
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
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
    onOpenAlbums: (TidalArtist) -> Unit,
    releaseVersionPreference: ReleaseVersionPreference,
) {
    var artist by remember(artistId, initialArtist) { mutableStateOf(initialArtist) }
    var tracks by remember(artistId) { mutableStateOf<List<TidalTrack>?>(null) }
    var albumGroups by remember(artistId) { mutableStateOf<TidalArtistAlbums?>(null) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId, releaseVersionPreference) {
        loading = true
        error = null
        try {
            val loadedArtist = withContext(Dispatchers.IO) { runCatching { apiClient.artist(artistId) }.getOrNull() }
            val loadedTracks = withContext(Dispatchers.IO) { apiClient.artistTracks(artistId) }
            val loadedAlbumGroups = withContext(Dispatchers.IO) {
                apiClient.artistAlbumGroups(
                    artistId,
                    limitPerGroup = ARTIST_ALBUM_PREVIEW_COUNT,
                    releaseVersionPreference = releaseVersionPreference,
                )
            }
            artist = loadedArtist?.copy(
                name = loadedArtist.name.ifBlank { initialArtist?.name.orEmpty() },
                artworkUrl = loadedArtist.artworkUrl ?: initialArtist?.artworkUrl,
            ) ?: initialArtist
            tracks = loadedTracks
            albumGroups = loadedAlbumGroups
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            tracks = null
            albumGroups = null
            error = "Artist unavailable"
        } finally {
            loading = false
        }
    }

    val safeTracks = tracks.orEmpty()
    val safeAlbumGroups = albumGroups ?: TidalArtistAlbums()
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
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
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
                safeTracks.isEmpty() && safeAlbumGroups.all.isEmpty() -> item { StatusText("No artist content found") }
                else -> {
                    section("Top Tracks", safeTracks.take(12)) { track ->
                        TidalResultChip(
                            label = track.title,
                            secondaryLabel = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Track" },
                            artworkUrl = track.artworkUrl,
                            fallback = "♪",
                            onClick = {
                                if (safeTracks.size > 1) {
                                    onPlayQueue(safeTracks, safeTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                                } else {
                                    onPlayTrack(track)
                                }
                            },
                        )
                    }
                    albumSection("Albums", safeAlbumGroups.albums, title, onOpenAlbum)
                    albumSection("EPs & Singles", safeAlbumGroups.epsAndSingles, title, onOpenAlbum)
                    albumSection("Compilations", safeAlbumGroups.compilations, title, onOpenAlbum)
                    albumSection("Other", safeAlbumGroups.other, title, onOpenAlbum)
                    if (safeAlbumGroups.all.isNotEmpty()) {
                        item {
                            MoreAlbumsChip {
                                onOpenAlbums(artist ?: TidalArtist(artistId, title, artworkUrl))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistAlbumsScreen(
    apiClient: TidalApiClient,
    artistId: String,
    initialArtist: TidalArtist?,
    onOpenAlbum: (TidalAlbum) -> Unit,
    releaseVersionPreference: ReleaseVersionPreference,
) {
    var artist by remember(artistId, initialArtist) { mutableStateOf(initialArtist) }
    var albumGroups by remember(artistId) { mutableStateOf<TidalArtistAlbums?>(null) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId, releaseVersionPreference) {
        loading = true
        error = null
        try {
            val loadedArtist = withContext(Dispatchers.IO) { runCatching { apiClient.artist(artistId) }.getOrNull() }
            val loadedAlbums = withContext(Dispatchers.IO) {
                apiClient.artistAlbumGroups(
                    artistId,
                    limitPerGroup = ARTIST_ALBUM_FULL_LIMIT,
                    releaseVersionPreference = releaseVersionPreference,
                )
            }
            artist = loadedArtist?.copy(
                name = loadedArtist.name.ifBlank { initialArtist?.name.orEmpty() },
                artworkUrl = loadedArtist.artworkUrl ?: initialArtist?.artworkUrl,
            ) ?: initialArtist
            albumGroups = loadedAlbums
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            albumGroups = null
            error = "Albums unavailable"
        } finally {
            loading = false
        }
    }

    val safeAlbumGroups = albumGroups ?: TidalArtistAlbums()
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
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ArtistHeader(
                    name = title,
                    bitmap = art.bitmap,
                    accent = accent,
                )
            }
            item { SectionHeader("Albums") }
            when {
                loading -> item { StatusText("Loading albums…") }
                error != null -> item { StatusText(error.orEmpty()) }
                safeAlbumGroups.all.isEmpty() -> item { StatusText("No albums found") }
                else -> {
                    albumSection("Albums", safeAlbumGroups.albums, title, onOpenAlbum)
                    albumSection("EPs & Singles", safeAlbumGroups.epsAndSingles, title, onOpenAlbum)
                    albumSection("Compilations", safeAlbumGroups.compilations, title, onOpenAlbum)
                    albumSection("Other", safeAlbumGroups.other, title, onOpenAlbum)
                }
            }
        }
    }
}

private const val ARTIST_ALBUM_PREVIEW_COUNT = 12
private const val ARTIST_ALBUM_FULL_LIMIT = 100

private fun <T> androidx.wear.compose.foundation.lazy.ScalingLazyListScope.section(
    title: String,
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item { SectionHeader(title) }
    items.forEach { element -> item { itemContent(element) } }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.albumSection(
    title: String,
    albums: List<TidalAlbum>,
    fallbackArtist: String,
    onOpenAlbum: (TidalAlbum) -> Unit,
) {
    section(title, albums) { album ->
        AlbumChip(album = album, fallbackArtist = fallbackArtist, onOpenAlbum = onOpenAlbum)
    }
}

@Composable
private fun AlbumChip(
    album: TidalAlbum,
    fallbackArtist: String,
    onOpenAlbum: (TidalAlbum) -> Unit,
) {
    TidalResultChip(
        label = album.title,
        secondaryLabel = album.artist.ifBlank { fallbackArtist },
        artworkUrl = album.artworkUrl,
        fallback = "▣",
        onClick = { onOpenAlbum(album) },
    )
}

@Composable
private fun MoreAlbumsChip(onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = onClick,
        label = {
            Text(
                text = "View full discography",
                color = TidalColors.Black,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        secondaryLabel = {
            Text(
                text = "All releases",
                color = TidalColors.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        colors = ChipDefaults.primaryChipColors(
            backgroundColor = TidalColors.Cyan,
            contentColor = TidalColors.Black,
        ),
    )
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
