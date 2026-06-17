package com.tidal.wear.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.tidal.wear.BuildConfig
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.AddTrackToPlaylistOutcome
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

@Composable
fun AddToPlaylistSheet(
    track: TidalTrack,
    apiClient: TidalApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playlists by remember(track.id) { mutableStateOf<List<TidalPlaylist>>(emptyList()) }
    var loading by remember(track.id) { mutableStateOf(true) }
    var loadError by remember(track.id) { mutableStateOf<String?>(null) }
    var addingTarget by remember(track.id) { mutableStateOf<TidalPlaylist?>(null) }
    var addError by remember(track.id) { mutableStateOf<String?>(null) }
    var createError by remember(track.id) { mutableStateOf<String?>(null) }
    var successMessage by remember(track.id) { mutableStateOf<String?>(null) }
    var creatingPlaylist by remember(track.id) { mutableStateOf(false) }
    var reloadToken by remember(track.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    fun addToPlaylist(playlist: TidalPlaylist) {
        if (addingTarget != null || creatingPlaylist || successMessage != null) return
        addingTarget = playlist
        addError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { apiClient.addTrackToPlaylist(playlist.id, track.id) }
            }.onSuccess { outcome ->
                val prefix = when (outcome) {
                    AddTrackToPlaylistOutcome.Added -> "Added to"
                    AddTrackToPlaylistOutcome.AlreadyPresent -> "Already in"
                }
                successMessage = "$prefix ${playlist.title}"
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                addError = playlistAddErrorMessage(throwable, playlist.title)
            }
            addingTarget = null
        }
    }

    fun createPlaylistAndAddTrack() {
        if (addingTarget != null || creatingPlaylist || successMessage != null) return
        creatingPlaylist = true
        addError = null
        createError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val playlist = apiClient.createPlaylist(
                        name = defaultUntidyPlaylistName(),
                        description = "Faux/test playlist created from Untidy Wear OS; do not delete automatically.",
                    )
                    val outcome = apiClient.addTrackToPlaylist(playlist.id, track.id)
                    playlist to outcome
                }
            }.onSuccess { (playlist, outcome) ->
                val prefix = when (outcome) {
                    AddTrackToPlaylistOutcome.Added -> "Added to"
                    AddTrackToPlaylistOutcome.AlreadyPresent -> "Already in"
                }
                playlists = listOf(playlist) + playlists.filterNot { it.id == playlist.id }
                successMessage = "$prefix ${playlist.title}"
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                createError = playlistCreateErrorMessage(throwable)
            }
            creatingPlaylist = false
        }
    }

    LaunchedEffect(track.id, reloadToken) {
        loading = true
        loadError = null
        runCatching { withContext(Dispatchers.IO) { apiClient.editablePlaylists() } }
            .onSuccess { playlists = it }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                loadError = playlistLoadErrorMessage(throwable)
            }
        loading = false
    }
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(1_200)
            onBack()
        }
    }

    BackHandler(enabled = true) {
        if (addingTarget == null) onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TidalColors.Black)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Add to playlist",
            color = TidalColors.Cyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" · "),
            color = TidalColors.OnSurfaceMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.86f).padding(top = 2.dp),
        )

        when {
            loading -> CenterStatus("Loading playlists…")
            successMessage != null -> CenterStatus(successMessage.orEmpty(), icon = Icons.Filled.Check, iconTint = TidalColors.Cyan)
            loadError != null && playlists.isEmpty() -> CenterStatus(
                text = loadError.orEmpty(),
                icon = Icons.Filled.Error,
                iconTint = Color(0xFFFF8A80),
                primaryAction = "Retry" to { reloadToken += 1 },
                secondaryAction = "Cancel" to onBack,
            )
            addingTarget != null -> CenterStatus("Adding to ${addingTarget?.title.orEmpty()}…")
            creatingPlaylist -> CenterStatus("Creating playlist…")
            else -> PlaylistChooser(
                playlists = playlists,
                addError = addError ?: createError,
                showCreatePlaylist = BuildConfig.DEBUG,
                onSelect = ::addToPlaylist,
                onCreatePlaylist = ::createPlaylistAndAddTrack,
                onReload = { reloadToken += 1 },
            )
        }
    }
}

@Composable
private fun PlaylistChooser(
    playlists: List<TidalPlaylist>,
    addError: String?,
    showCreatePlaylist: Boolean,
    onSelect: (TidalPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onReload: () -> Unit,
) {
    TransformingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 6.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (addError != null) {
            item { StatusLine(addError, Color(0xFFFF8A80)) }
        }
        if (showCreatePlaylist) {
            item {
                CreatePlaylistRow(onClick = onCreatePlaylist)
            }
        }
        if (playlists.isEmpty()) {
            item { StatusLine("No editable playlists found", TidalColors.OnSurfaceMuted) }
            item { SmallButton("Reload", onReload) }
        } else {
            playlists.forEach { playlist ->
                item {
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onSelect(playlist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: TidalPlaylist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = TidalColors.Cyan, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = TidalColors.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlist.creator.ifBlank { "Playlist" },
                color = TidalColors.OnSurfaceMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DisabledPlaylistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TidalColors.Surface.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TidalColors.OnSurfaceMuted, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TidalColors.OnSurfaceMuted, fontSize = 13.sp, maxLines = 1)
            Text(subtitle, color = TidalColors.OnSurfaceMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CenterStatus(
    text: String,
    icon: ImageVector? = null,
    iconTint: Color = TidalColors.Cyan,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = text,
            color = TidalColors.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (primaryAction != null || secondaryAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondaryAction?.let { SmallButton(it.first, it.second) }
                primaryAction?.let { SmallButton(it.first, it.second) }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(0.86f).padding(vertical = 6.dp),
    )
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = TidalColors.SurfaceHigh,
            contentColor = TidalColors.White,
        ),
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(17.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CreatePlaylistRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = TidalColors.Cyan, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("New debug test playlist", color = TidalColors.White, fontSize = 13.sp, maxLines = 1)
            Text("Creates Untidy Test + adds track", color = TidalColors.OnSurfaceMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun defaultUntidyPlaylistName(): String = "Untidy Test - ${LocalDate.now()}"

private fun playlistLoadErrorMessage(throwable: Throwable): String = when (throwable) {
    is HttpException -> when (throwable.code()) {
        401 -> "Sign in again"
        403 -> "Playlist access denied"
        else -> "Couldn't load playlists"
    }
    is IOException -> "No connection"
    else -> "Couldn't load playlists"
}

private fun playlistCreateErrorMessage(throwable: Throwable): String = when (throwable) {
    is HttpException -> when (throwable.code()) {
        401 -> "Sign in again"
        403 -> "Playlist access denied"
        429 -> "Too many requests"
        in 500..599 -> "TIDAL unavailable"
        else -> "Couldn't create playlist"
    }
    is IOException -> "No connection"
    else -> "Couldn't create playlist"
}

private fun playlistAddErrorMessage(throwable: Throwable, playlistTitle: String): String = when (throwable) {
    is IllegalArgumentException -> "Track unavailable"
    is HttpException -> when (throwable.code()) {
        401 -> "Sign in again"
        403 -> "Playlist access denied"
        404 -> "Playlist not found"
        409 -> "Already in $playlistTitle"
        429 -> "Too many requests"
        in 500..599 -> "TIDAL unavailable"
        else -> "Couldn't add to $playlistTitle"
    }
    is IOException -> "No connection"
    else -> "Couldn't add to $playlistTitle"
}
