package com.tidal.wear.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import coil.size.Size
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalSearchResult
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KeyboardInputHostAlpha = 0.02f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    apiClient: TidalApiClient,
    onPlayTrack: (TidalTrack) -> Unit,
    onOpenAlbum: (TidalAlbum) -> Unit,
    onOpenPlaylist: (TidalPlaylist) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val sidePadding = (LocalConfiguration.current.screenWidthDp * 0.12f).dp
    val imeVisible = WindowInsets.isImeVisible
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var submittedQuery by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TidalSearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var requestKeyboardTick by remember { mutableIntStateOf(1) }
    var inputFocusedOnce by remember { mutableStateOf(false) }
    var keyboardShownOnce by remember { mutableStateOf(false) }

    LaunchedEffect(requestKeyboardTick) {
        if (requestKeyboardTick > 0) {
            delay(120)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(imeVisible, inputFocusedOnce, query.text, submittedQuery) {
        if (imeVisible) {
            keyboardShownOnce = true
        } else if (keyboardShownOnce && inputFocusedOnce && query.text.isBlank() && submittedQuery.isBlank()) {
            onCancel()
        }
    }

    fun searchNow() {
        val q = query.text.trim()
        if (q.isBlank() || loading) return
        submittedQuery = q
        result = null
        error = null
        loading = true
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            try {
                result = withContext(Dispatchers.IO) { apiClient.search(q) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                result = null
                error = "Search unavailable"
            } finally {
                loading = false
            }
        }
    }

    fun resetSearch() {
        query = TextFieldValue("")
        submittedQuery = ""
        result = null
        error = null
        inputFocusedOnce = false
        keyboardShownOnce = false
        requestKeyboardTick += 1
    }

    fun editSearch() {
        query = TextFieldValue(submittedQuery, selection = TextRange(submittedQuery.length))
        submittedQuery = ""
        error = null
        inputFocusedOnce = false
        keyboardShownOnce = false
        requestKeyboardTick += 1
    }

    BackHandler(enabled = submittedQuery.isBlank()) { onCancel() }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        if (submittedQuery.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SearchInput(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = ::searchNow,
                    focusRequester = focusRequester,
                    onFocusChanged = { isFocused ->
                        if (isFocused) {
                            inputFocusedOnce = true
                        } else if (inputFocusedOnce && query.text.isBlank() && submittedQuery.isBlank()) {
                            onCancel()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding).alpha(KeyboardInputHostAlpha),
                )
            }
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    SearchResultsTopBar(
                        query = submittedQuery,
                        onEdit = ::editSearch,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                when {
                    loading -> item { SearchingIndicator() }
                    error != null -> item { StatusText(error.orEmpty()) }
                    result.isNullOrEmpty() -> item { StatusText("No results found") }
                    else -> {
                        val safeResult = result ?: TidalSearchResult()
                        section("Songs", safeResult.tracks.take(6)) { track ->
                            SearchResultChip(
                                label = track.title,
                                secondaryLabel = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                                artworkUrl = track.artworkUrl,
                                fallback = "♪",
                                onClick = { onPlayTrack(track) },
                            )
                        }
                        section("Albums", safeResult.albums.take(4)) { album ->
                            SearchResultChip(
                                label = album.title,
                                secondaryLabel = album.artist.ifBlank { "Album" },
                                artworkUrl = album.artworkUrl,
                                fallback = "▣",
                                onClick = { onOpenAlbum(album) },
                            )
                        }
                        section("Artists", safeResult.artists.take(4)) { artist ->
                            SearchResultChip(
                                label = artist.name,
                                secondaryLabel = "Artist",
                                artworkUrl = artist.artworkUrl,
                                fallback = "★",
                                onClick = { Toast.makeText(context, "Artists coming soon", Toast.LENGTH_SHORT).show() },
                            )
                        }
                        section("Playlists", safeResult.playlists.take(4)) { playlist ->
                            SearchResultChip(
                                label = playlist.title,
                                secondaryLabel = playlist.creator.ifBlank { "Playlist" },
                                artworkUrl = playlist.artworkUrl,
                                fallback = "≡",
                                onClick = { onOpenPlaylist(playlist) },
                            )
                        }
                    }
                }
                if (!loading) {
                    item {
                        Chip(
                            onClick = ::resetSearch,
                            colors = ChipDefaults.secondaryChipColors(
                                backgroundColor = TidalColors.Surface,
                                contentColor = TidalColors.White,
                                iconColor = TidalColors.Cyan,
                            ),
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text("New search", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun TidalSearchResult?.isNullOrEmpty(): Boolean = this == null ||
    (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty())

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
private fun SearchResultsTopBar(
    query: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Search results",
                color = TidalColors.Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = query,
                color = TidalColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TidalColors.Surface.copy(alpha = 0.45f))
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit search",
                tint = TidalColors.Cyan,
                modifier = Modifier.size(20.dp),
            )
        }
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
private fun SearchResultChip(
    label: String,
    secondaryLabel: String,
    artworkUrl: String?,
    fallback: String,
    onClick: () -> Unit,
) {
    val art = rememberArtworkPalette(artworkUrl, Size(96, 96))
    val accent = art.palette.accentColor()
    val background = lerp(TidalColors.Surface, accent, 0.28f)
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = background,
            contentColor = TidalColors.White,
            secondaryContentColor = lerp(TidalColors.OnSurfaceDim, Color.White, 0.28f),
            iconColor = TidalColors.White,
        ),
        icon = {
            ArtworkThumb(
                label = label,
                fallback = fallback,
                bitmap = art.bitmap,
                accent = accent,
            )
        },
        label = { OneLineText(label) },
        secondaryLabel = { OneLineText(secondaryLabel) },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Composable
private fun ArtworkThumb(
    label: String,
    fallback: String,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .size(ChipDefaults.LargeIconSize)
            .clip(RoundedCornerShape(6.dp))
            .background(lerp(TidalColors.SurfaceHigh, accent, 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: fallback,
                color = TidalColors.White,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun OneLineText(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SearchingIndicator() {
    val pulse by rememberInfiniteTransition(label = "searching").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "searchingPulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .background(TidalColors.Cyan.copy(alpha = pulse), CircleShape),
        )
        Text(
            text = "Searching…",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SearchInput(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        singleLine = true,
        textStyle = TextStyle(color = TidalColors.White, fontSize = 14.sp, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(TidalColors.Cyan),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TidalColors.Surface, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (query.text.isBlank()) {
                    Text("Search TIDAL", color = TidalColors.OnSurfaceMuted, fontSize = 14.sp)
                }
                innerTextField()
            }
        },
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
