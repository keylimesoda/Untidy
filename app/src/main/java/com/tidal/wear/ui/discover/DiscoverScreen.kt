package com.tidal.wear.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalDiscoverSection
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.components.TidalResultChip
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.offline.rememberNetworkAvailable
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiscoverScreen(
    apiClient: TidalApiClient,
    onOpenAlbum: (TidalAlbum) -> Unit,
    onOpenPlaylist: (TidalPlaylist) -> Unit,
    onOpenArtist: (TidalArtist) -> Unit,
    onPlayTrack: (TidalTrack) -> Unit,
    onPlayQueue: (List<TidalTrack>, Int) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<TidalDiscoverSection>>(emptyList()) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var loadTick by remember { mutableIntStateOf(0) }
    val networkAvailable = rememberNetworkAvailable()
    val selectedSection = selectedTitle?.let { title -> sections.firstOrNull { it.title == title } }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    fun loadDiscover() {
        if (!networkAvailable) {
            sections = emptyList()
            selectedTitle = null
            loading = false
            error = true
            return
        }
        loading = true
        error = false
        scope.launch {
            try {
                sections = withContext(Dispatchers.IO) { apiClient.discoverSections() }
                selectedTitle = selectedTitle?.takeIf { title -> sections.any { it.title == title } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                sections = emptyList()
                error = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(loadTick, networkAvailable) { loadDiscover() }
    LaunchedEffect(selectedTitle, loading) {
        if (!loading) listState.scrollToItem(0)
    }
    BackHandler(enabled = selectedTitle != null) { selectedTitle = null }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = WearListPadding.RoundScreenCompact,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { DiscoverTitle(selectedSection?.title ?: "Discover") }
            when {
                loading -> item { LoadingDiscover() }
                error -> {
                    item { ErrorRow(networkAvailable = networkAvailable, onClick = { loadTick++ }) }
                    if (!networkAvailable) {
                        item { DownloadsShortcutChip(onClick = onOpenDownloads) }
                    }
                }
                sections.isEmpty() -> item { EmptyDiscover() }
                selectedSection == null -> sections.forEach { section ->
                    item {
                        ShelfRow(
                            title = section.title,
                            secondary = section.summaryLabel(),
                            onClick = { selectedTitle = section.title },
                        )
                    }
                }
                else -> selectedSection.result.toItems().forEach { item ->
                    item {
                        when (item) {
                            is DiscoverItem.TrackItem -> TidalResultChip(
                                label = item.track.title,
                                secondaryLabel = listOf(item.track.artist, item.track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Track" },
                                artworkUrl = item.track.artworkUrl,
                                fallback = "♪",
                                onClick = {
                                    if (selectedSection.result.tracks.size > 1) {
                                        onPlayQueue(selectedSection.result.tracks, item.queueIndex)
                                    } else {
                                        onPlayTrack(item.track)
                                    }
                                },
                            )
                            is DiscoverItem.AlbumItem -> TidalResultChip(
                                label = item.album.title,
                                secondaryLabel = item.album.artist.ifBlank { "Album" },
                                artworkUrl = item.album.artworkUrl,
                                fallback = "▣",
                                onClick = { onOpenAlbum(item.album) },
                            )
                            is DiscoverItem.PlaylistItem -> TidalResultChip(
                                label = item.playlist.title,
                                secondaryLabel = item.playlist.creator.ifBlank { "Playlist" },
                                artworkUrl = item.playlist.artworkUrl,
                                fallback = "≡",
                                onClick = { onOpenPlaylist(item.playlist) },
                            )
                            is DiscoverItem.ArtistItem -> TidalResultChip(
                                label = item.artist.name,
                                secondaryLabel = "Artist",
                                artworkUrl = item.artist.artworkUrl,
                                fallback = "★",
                                onClick = { onOpenArtist(item.artist) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface DiscoverItem {
    data class TrackItem(val track: TidalTrack, val queueIndex: Int) : DiscoverItem
    data class AlbumItem(val album: TidalAlbum) : DiscoverItem
    data class PlaylistItem(val playlist: TidalPlaylist) : DiscoverItem
    data class ArtistItem(val artist: TidalArtist) : DiscoverItem
}

private fun TidalSearchResult.toItems(): List<DiscoverItem> =
    tracks.take(12).mapIndexed { index, track -> DiscoverItem.TrackItem(track, index) } +
        albums.take(8).map { DiscoverItem.AlbumItem(it) } +
        playlists.take(8).map { DiscoverItem.PlaylistItem(it) } +
        artists.take(6).map { DiscoverItem.ArtistItem(it) }

@Composable
private fun DownloadsShortcutChip(onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.SurfaceHigh,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
            iconColor = TidalColors.Cyan,
        ),
        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
        label = { RowText("Open Downloads") },
        secondaryLabel = { RowText("Play saved music") },
    )
}

private fun TidalDiscoverSection.summaryLabel(): String = subtitle.ifBlank {
    when {
        result.tracks.isNotEmpty() -> "${result.tracks.size} personalized tracks"
        result.albums.isNotEmpty() -> "${result.albums.size} albums"
        result.playlists.isNotEmpty() -> "${result.playlists.size} playlists"
        else -> "Personalized mix"
    }
}

@Composable
private fun DiscoverTitle(title: String) {
    Text(
        text = title,
        color = TidalColors.White,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun ShelfRow(
    title: String,
    secondary: String,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceDim,
            iconColor = TidalColors.Cyan,
        ),
        icon = {
            val icon = when (title) {
                "New for You" -> Icons.Filled.Album
                "Daily Mixes" -> Icons.Filled.LibraryMusic
                else -> Icons.Filled.Star
            }
            Icon(icon, contentDescription = null)
        },
        label = { RowText(title) },
        secondaryLabel = { RowText(secondary) },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Composable
private fun LoadingDiscover() {
    val pulse by rememberInfiniteTransition(label = "discoverLoading").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "discoverLoadingPulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .background(TidalColors.Cyan.copy(alpha = pulse), CircleShape),
        )
        Text(
            text = "Loading recommendations…",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyDiscover() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text("No personalized mixes yet", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(
            "Listen more to get personalized mixes.",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ErrorRow(networkAvailable: Boolean, onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
            iconColor = TidalColors.Cyan,
        ),
        label = { RowText(if (networkAvailable) "Discover unavailable" else "Connect to discover") },
        secondaryLabel = { RowText(if (networkAvailable) "Check recommendations access · tap to retry" else "Downloads still work offline") },
    )
}

@Composable
private fun RowText(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
