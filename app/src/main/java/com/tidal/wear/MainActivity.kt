package com.tidal.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.auth.AuthState
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.PlaybackActions
import com.tidal.wear.core.playback.PlaybackQueueStore
import com.tidal.wear.core.playback.TidalMediaService
import com.tidal.wear.playback.NowPlayingViewModel
import com.tidal.wear.ui.album.AlbumScreen
import com.tidal.wear.ui.discover.DiscoverScreen
import com.tidal.wear.ui.library.LibraryScreen
import com.tidal.wear.ui.onboarding.OnboardingScreen
import com.tidal.wear.ui.playlist.PlaylistScreen
import com.tidal.wear.ui.search.SearchScreen
import com.tidal.wear.ui.settings.SettingsScreen
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val HomePrimarySurface = Color(0xFF1A1A1A)
private val HomeSecondarySurface = Color(0xFF1A1A1A)
private val HomeDisabledContent = Color(0x80FFFFFF)
private val HomeHorizontalPadding = 10.dp

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent { TidalWearApp() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Discover = "discover"
    const val Search = "search"
    const val Album = "album/{albumId}"
    const val Playlist = "playlist/{playlistId}"
    const val Library = "library"
    const val Settings = "settings"

    fun album(albumId: String): String = "album/${Uri.encode(albumId)}"
    fun playlist(playlistId: String): String = "playlist/${Uri.encode(playlistId)}"
}

private data class HomePlaybackSummary(
    val track: TidalTrack? = null,
    val isPlaying: Boolean = false,
)

@Composable
private fun TidalWearApp() {
    MaterialTheme {
        AppScaffold(timeText = {}) {
            val navController = rememberSwipeDismissableNavController()
            val context = LocalContext.current
            val authRepository = remember(context) { TidalAuthRepositoryProvider.get(context.applicationContext) }
            val apiClient = remember(authRepository) { TidalApiClient(authRepository) }
            val nowPlayingViewModel = viewModel<NowPlayingViewModel>()
            val homePlayback by remember(nowPlayingViewModel) {
                nowPlayingViewModel.state
                    .map { HomePlaybackSummary(track = it.track, isPlaying = it.isPlaying) }
                    .distinctUntilChanged()
            }.collectAsState(initial = HomePlaybackSummary())
            val authState by authRepository.authState.collectAsState(initial = AuthState.Initializing)

            LaunchedEffect(authState) {
                when (authState) {
                    AuthState.UserSignedIn -> navController.navigateAndClear(Routes.Home)
                    AuthState.Anonymous,
                    AuthState.Initializing,
                    -> navController.navigateAndClear(Routes.Onboarding)
                }
            }

            fun openPlayer() = context.startActivity(Intent(context, PlayerActivity::class.java))
            fun playTrack(track: TidalTrack) {
                context.startTrackPlayback(track)
                openPlayer()
            }
            fun playQueue(tracks: List<TidalTrack>, startIndex: Int) {
                context.startQueuePlayback(tracks, startIndex)
                openPlayer()
            }
            fun openAlbum(album: TidalAlbum) {
                AlbumSelectionStore.put(album)
                navController.navigate(Routes.album(album.id))
            }
            fun openPlaylist(playlist: TidalPlaylist) {
                PlaylistSelectionStore.put(playlist)
                navController.navigate(Routes.playlist(playlist.id))
            }

            SwipeDismissableNavHost(navController = navController, startDestination = Routes.Onboarding) {
                composable(Routes.Onboarding) {
                    OnboardingScreen(
                        authRepository = authRepository,
                        onAuthenticated = { navController.navigateAndClear(Routes.Home) },
                    )
                }
                composable(Routes.Home) {
                    HomeScreen(
                        navController = navController,
                        authState = authState,
                        track = homePlayback.track,
                        isPlaying = homePlayback.isPlaying,
                        onNowPlaying = ::openPlayer,
                        onResume = {
                            homePlayback.track?.let {
                                context.resumeTrackPlayback()
                                openPlayer()
                            } ?: navController.navigate(Routes.Search)
                        },
                        onOffline = { Toast.makeText(context, "Downloads coming soon", Toast.LENGTH_SHORT).show() },
                    )
                }
                composable(Routes.Discover) {
                    DiscoverScreen(
                        apiClient = apiClient,
                        onOpenAlbum = ::openAlbum,
                        onOpenPlaylist = ::openPlaylist,
                        onPlayTrack = ::playTrack,
                        onPlayQueue = ::playQueue,
                    )
                }
                composable(Routes.Search) {
                    SearchScreen(
                        apiClient = apiClient,
                        onPlayTrack = ::playTrack,
                        onOpenAlbum = ::openAlbum,
                        onOpenPlaylist = ::openPlaylist,
                        onCancel = {
                            if (!navController.popBackStack()) {
                                navController.navigateAndClear(Routes.Home)
                            }
                        },
                    )
                }
                composable(Routes.Album) { entry ->
                    val albumId = entry.arguments?.getString("albumId")?.let(Uri::decode).orEmpty()
                    AlbumScreen(
                        apiClient = apiClient,
                        albumId = albumId,
                        initialAlbum = AlbumSelectionStore.get(albumId),
                        onPlayQueue = ::playQueue,
                    )
                }
                composable(Routes.Playlist) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId")?.let(Uri::decode).orEmpty()
                    PlaylistScreen(
                        apiClient = apiClient,
                        playlistId = playlistId,
                        initialPlaylist = PlaylistSelectionStore.get(playlistId),
                        onPlayQueue = ::playQueue,
                    )
                }
                composable(Routes.Library) {
                    LibraryScreen(
                        apiClient = apiClient,
                        onOpenAlbum = ::openAlbum,
                        onOpenPlaylist = ::openPlaylist,
                        onPlayTrack = ::playTrack,
                    )
                }
                composable(Routes.Settings) { SettingsScreen(authRepository = authRepository, onSignedOut = { navController.navigateAndClear(Routes.Onboarding) }) }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    navController: NavHostController,
    authState: AuthState,
    track: TidalTrack?,
    isPlaying: Boolean,
    onNowPlaying: () -> Unit,
    onResume: () -> Unit,
    onOffline: () -> Unit,
) {
    val signedIn = authState == AuthState.UserSignedIn
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { HomeWordmark() }
            item {
                HomePrimaryAction(
                    signedIn = signedIn,
                    track = track,
                    isPlaying = isPlaying,
                    onSignedOutClick = { navController.navigate(Routes.Onboarding) },
                    onSearchClick = { navController.navigate(Routes.Search) },
                    onNowPlayingClick = onNowPlaying,
                    onResumeClick = onResume,
                )
            }
            if (signedIn) {
                if (track != null) {
                    item {
                        HomeListChip(
                            label = "Search",
                            secondaryLabel = "Find music on TIDAL",
                            icon = Icons.Filled.Search,
                            onClick = { navController.navigate(Routes.Search) },
                        )
                    }
                }
                item {
                        HomeListChip(
                            label = "Discover",
                            secondaryLabel = "Personalized mixes",
                            icon = Icons.Filled.Star,
                            onClick = { navController.navigate(Routes.Discover) },
                        )
                }
                item {
                    HomeListChip(
                        label = "Library",
                        secondaryLabel = "Your collection",
                        icon = Icons.Filled.LibraryMusic,
                        onClick = { navController.navigate(Routes.Library) },
                    )
                }
                item {
                    HomeListChip(
                        label = "Downloads",
                        secondaryLabel = "Coming soon",
                        icon = Icons.Filled.Download,
                        enabled = false,
                        onClick = onOffline,
                    )
                }
                item {
                    HomeListChip(
                        label = "Settings",
                        secondaryLabel = "Account & playback",
                        icon = Icons.Filled.Settings,
                        onClick = { navController.navigate(Routes.Settings) },
                    )
                }
            }
            item { HomeFooter() }
        }
    }
}

@Composable
private fun HomeWordmark() {
    Text(
        text = "UNTIDY",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeHorizontalPadding, vertical = 4.dp),
        color = TidalColors.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HomePrimaryAction(
    signedIn: Boolean,
    track: TidalTrack?,
    isPlaying: Boolean,
    onSignedOutClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNowPlayingClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    val hasTrack = track != null
    val icon: ImageVector
    val label: String
    val secondaryLabel: String
    val onClick: () -> Unit

    when {
        !signedIn -> {
            icon = Icons.Filled.Login
            label = "Sign in to TIDAL"
            secondaryLabel = "Connect your account"
            onClick = onSignedOutClick
        }
        isPlaying && hasTrack -> {
            icon = Icons.Filled.Equalizer
            label = "Now playing"
            secondaryLabel = track.metadataLine()
            onClick = onNowPlayingClick
        }
        hasTrack -> {
            icon = Icons.Filled.PlayArrow
            label = "Resume"
            secondaryLabel = track.metadataLine()
            onClick = onResumeClick
        }
        else -> {
            icon = Icons.Filled.Search
            label = "Search"
            secondaryLabel = "Find music on TIDAL"
            onClick = onSearchClick
        }
    }

    HomeRowSurface(
        label = label,
        secondaryLabel = secondaryLabel,
        icon = icon,
        iconTint = TidalColors.Cyan,
        minHeight = 56.dp,
        surfaceColor = HomePrimarySurface,
        onClick = onClick,
    )
}

@Composable
private fun HomeListChip(
    label: String,
    secondaryLabel: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    HomeRowSurface(
        label = label,
        secondaryLabel = secondaryLabel,
        icon = icon,
        enabled = enabled,
        iconTint = if (enabled) TidalColors.Cyan else HomeDisabledContent,
        minHeight = 48.dp,
        surfaceColor = HomeSecondarySurface,
        onClick = onClick,
    )
}

@Composable
private fun HomeRowSurface(
    label: String,
    secondaryLabel: String,
    icon: ImageVector,
    enabled: Boolean = true,
    iconTint: Color,
    minHeight: Dp,
    surfaceColor: Color,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.55f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeHorizontalPadding)
            .heightIn(min = minHeight)
            .background(surfaceColor, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(TidalColors.Black, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = iconTint)
        }
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            Text(
                text = label,
                color = TidalColors.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondaryLabel,
                color = TidalColors.OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeFooter() {
    Text(
        text = "Plays from TIDAL",
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HomeHorizontalPadding, end = HomeHorizontalPadding, top = 8.dp, bottom = 12.dp),
        color = TidalColors.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

private fun TidalTrack?.metadataLine(): String = listOf(
    this?.title.orEmpty(),
    this?.artist.orEmpty(),
).filter { it.isNotBlank() }.joinToString(" \u00B7 ").ifBlank { "TIDAL" }

private fun Context.startTrackPlayback(track: TidalTrack) {
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_PLAY_TRACK)
        .putExtra(PlaybackActions.EXTRA_TRACK_ID, track.id)
        .putExtra(PlaybackActions.EXTRA_TITLE, track.title)
        .putExtra(PlaybackActions.EXTRA_ARTIST, track.artist)
        .putExtra(PlaybackActions.EXTRA_ALBUM, track.album)
        .putExtra(PlaybackActions.EXTRA_ARTWORK_URL, track.artworkUrl)
        .putExtra(PlaybackActions.EXTRA_DURATION_MS, track.durationMs)
    ContextCompat.startForegroundService(this, intent)
}

private fun Context.resumeTrackPlayback() {
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_RESUME)
    ContextCompat.startForegroundService(this, intent)
}

private fun Context.startQueuePlayback(tracks: List<TidalTrack>, startIndex: Int) {
    val queueId = PlaybackQueueStore.put(tracks)
    if (queueId.isBlank()) return
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_PLAY_QUEUE)
        .putExtra(PlaybackActions.EXTRA_QUEUE_ID, queueId)
        .putExtra(PlaybackActions.EXTRA_QUEUE_START_INDEX, startIndex)
    ContextCompat.startForegroundService(this, intent)
}

private object AlbumSelectionStore {
    private val albums = java.util.concurrent.ConcurrentHashMap<String, TidalAlbum>()

    fun put(album: TidalAlbum) {
        if (album.id.isNotBlank()) albums[album.id] = album
    }

    fun get(albumId: String): TidalAlbum? = albums[albumId]
}

private object PlaylistSelectionStore {
    private val playlists = java.util.concurrent.ConcurrentHashMap<String, TidalPlaylist>()

    fun put(playlist: TidalPlaylist) {
        if (playlist.id.isNotBlank()) playlists[playlist.id] = playlist
    }

    fun get(playlistId: String): TidalPlaylist? = playlists[playlistId]
}

private fun NavHostController.navigateAndClear(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}




