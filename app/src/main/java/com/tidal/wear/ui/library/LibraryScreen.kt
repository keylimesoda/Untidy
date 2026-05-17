package com.tidal.wear.ui.library

import android.widget.Toast
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.components.TidalResultChip
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    apiClient: TidalApiClient,
    onOpenAlbum: (TidalAlbum) -> Unit,
    onOpenPlaylist: (TidalPlaylist) -> Unit,
    onPlayTrack: (TidalTrack) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var favorites by remember { mutableStateOf<TidalSearchResult?>(null) }
    var selectedCategory by remember { mutableStateOf<LibraryCategory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var loadTick by remember { mutableStateOf(0) }

    fun loadLibrary() {
        loading = true
        error = false
        scope.launch {
            try {
                favorites = withContext(Dispatchers.IO) { apiClient.favorites() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                favorites = null
                error = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(loadTick) { loadLibrary() }
    BackHandler(enabled = selectedCategory != null) { selectedCategory = null }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                LibraryTitle(selectedCategory?.label ?: "Library")
            }
            when {
                loading -> item { LoadingCollection() }
                error -> item { ErrorRow(onClick = { loadTick++ }) }
                favorites.isEmpty() -> item {
                    EmptyCollection()
                }
                selectedCategory == null -> {
                    val safeFavorites = favorites ?: TidalSearchResult()
                    item {
                        CategoryRow(
                            label = LibraryCategory.Playlists.label,
                            count = safeFavorites.playlists.size,
                            icon = Icons.Filled.LibraryMusic,
                            onClick = { selectedCategory = LibraryCategory.Playlists },
                        )
                    }
                    item {
                        CategoryRow(
                            label = LibraryCategory.Albums.label,
                            count = safeFavorites.albums.size,
                            icon = Icons.Filled.Album,
                            onClick = { selectedCategory = LibraryCategory.Albums },
                        )
                    }
                    item {
                        CategoryRow(
                            label = LibraryCategory.Tracks.label,
                            count = safeFavorites.tracks.size,
                            icon = Icons.Filled.GraphicEq,
                            onClick = { selectedCategory = LibraryCategory.Tracks },
                        )
                    }
                    item {
                        CategoryRow(
                            label = LibraryCategory.Artists.label,
                            count = safeFavorites.artists.size,
                            icon = Icons.Filled.Person,
                            onClick = { selectedCategory = LibraryCategory.Artists },
                        )
                    }
                }
                else -> {
                    val safeFavorites = favorites ?: TidalSearchResult()
                    when (selectedCategory) {
                        LibraryCategory.Albums -> safeFavorites.albums.forEach { album ->
                            item {
                                LibraryResultRow(
                                    label = album.title,
                                    secondaryLabel = album.artist.ifBlank { "Album" },
                                    artworkUrl = album.artworkUrl,
                                    fallback = "▣",
                                    onClick = { onOpenAlbum(album) },
                                )
                            }
                        }
                        LibraryCategory.Tracks -> safeFavorites.tracks.forEach { track ->
                            item {
                                LibraryResultRow(
                                    label = track.title,
                                    secondaryLabel = track.artist.ifBlank { track.album.ifBlank { "Track" } },
                                    artworkUrl = track.artworkUrl,
                                    fallback = "♪",
                                    onClick = { onPlayTrack(track) },
                                )
                            }
                        }
                        LibraryCategory.Playlists -> safeFavorites.playlists.forEach { playlist ->
                            item {
                                LibraryResultRow(
                                    label = playlist.title,
                                    secondaryLabel = playlist.creator.ifBlank { "Playlist" },
                                    artworkUrl = playlist.artworkUrl,
                                    fallback = "≡",
                                    onClick = { onOpenPlaylist(playlist) },
                                )
                            }
                        }
                        LibraryCategory.Artists -> safeFavorites.artists.forEach { artist ->
                            item {
                                LibraryResultRow(
                                    label = artist.name,
                                    secondaryLabel = "Artist",
                                    artworkUrl = artist.artworkUrl,
                                    fallback = "★",
                                    onClick = { Toast.makeText(context, "Artists coming soon", Toast.LENGTH_SHORT).show() },
                                )
                            }
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}

private enum class LibraryCategory(val label: String) {
    Playlists("Playlists"),
    Albums("Albums"),
    Tracks("Tracks"),
    Artists("Artists"),
}

private fun TidalSearchResult?.isEmpty(): Boolean = this == null ||
    (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty())

@Composable
private fun LibraryTitle(title: String) {
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
private fun CategoryRow(
    label: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceDim,
            iconColor = TidalColors.Cyan,
        ),
        icon = { Icon(icon, contentDescription = null) },
        label = { RowText(label) },
        secondaryLabel = { RowText("$count") },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Composable
private fun LibraryResultRow(
    label: String,
    secondaryLabel: String,
    artworkUrl: String?,
    fallback: String,
    onClick: () -> Unit,
) {
    TidalResultChip(
        label = label,
        secondaryLabel = secondaryLabel,
        artworkUrl = artworkUrl,
        fallback = fallback,
        onClick = onClick,
    )
}

@Composable
private fun LoadingCollection() {
    val pulse by rememberInfiniteTransition(label = "libraryLoading").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "libraryLoadingPulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .background(TidalColors.Cyan.copy(alpha = pulse), CircleShape),
        )
        Text(
            text = "Loading collection…",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyCollection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text("Your collection is empty", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(
            "Albums, tracks, artists, and playlists you add on TIDAL will appear here.",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ErrorRow(onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
            iconColor = TidalColors.Cyan,
        ),
        label = { RowText("Library unavailable") },
        secondaryLabel = { RowText("Tap to retry") },
    )
}

@Composable
private fun RowText(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
