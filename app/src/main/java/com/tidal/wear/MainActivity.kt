package com.tidal.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
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
import com.tidal.wear.core.model.ReleaseVersionPreference
import com.tidal.wear.core.model.TidalAlbum
import com.tidal.wear.core.model.TidalArtist
import com.tidal.wear.core.model.TidalPlaylist
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.PlaybackActions
import com.tidal.wear.core.playback.PlaybackCommandTokenProvider
import com.tidal.wear.core.playback.PlaybackQueueStore
import com.tidal.wear.core.playback.TidalMediaService
import com.tidal.wear.playback.NowPlayingStateHolder
import com.tidal.wear.ui.album.AlbumScreen
import com.tidal.wear.ui.artist.ArtistAlbumsScreen
import com.tidal.wear.ui.artist.ArtistScreen
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.discover.DiscoverScreen
import com.tidal.wear.ui.library.LibraryScreen
import com.tidal.wear.ui.onboarding.OnboardingScreen
import com.tidal.wear.ui.playlist.PlaylistScreen
import com.tidal.wear.ui.search.SearchScreen
import com.tidal.wear.ui.settings.SettingsRepository
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

    private var routeRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeRequest = intent.getStringExtra(EXTRA_ROUTE)
        requestNotificationPermissionIfNeeded()
        setContent {
            TidalWearApp(
                routeRequest = routeRequest,
                onRouteRequestConsumed = { routeRequest = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRequest = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "com.tidal.wear.extra.ROUTE"
    }

    private fun requestNotificationPermissionIfNeeded() {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Discover = "discover"
    const val Search = "search"
    const val Album = "album/{albumId}"
    const val Playlist = "playlist/{playlistId}"
    const val Artist = "artist/{artistId}"
    const val ArtistAlbums = "artist/{artistId}/albums"
    const val Library = "library"
    const val Settings = "settings"

    fun album(albumId: String): String = "album/${Uri.encode(albumId)}"
    fun playlist(playlistId: String): String = "playlist/${Uri.encode(playlistId)}"
    fun artist(artistId: String): String = "artist/${Uri.encode(artistId)}"
    fun artistAlbums(artistId: String): String = "artist/${Uri.encode(artistId)}/albums"
}

private data class HomePlaybackSummary(
    val track: TidalTrack? = null,
    val isPlaying: Boolean = false,
)

@Composable
private fun TidalWearApp(
    routeRequest: String? = null,
    onRouteRequestConsumed: () -> Unit = {},
) {
    MaterialTheme {
        AppScaffold(timeText = {}) {
            val navController = rememberSwipeDismissableNavController()
            val context = LocalContext.current
            val authRepository = remember(context) { TidalAuthRepositoryProvider.get(context.applicationContext) }
            val settingsRepository = remember(context) { SettingsRepository(context.applicationContext) }
            val apiClient = remember(authRepository) { TidalApiClient(authRepository) }
            val appContext = remember(context) { context.applicationContext }
            val nowPlayingScope = rememberCoroutineScope()
            val nowPlayingHolder = remember(appContext) {
                NowPlayingStateHolder(appContext, nowPlayingScope, pollPosition = false)
            }
            DisposableEffect(nowPlayingHolder) {
                onDispose { nowPlayingHolder.release() }
            }
            val homePlayback by remember(nowPlayingHolder) {
                nowPlayingHolder.state
                    .map { HomePlaybackSummary(track = it.track, isPlaying = it.isPlaying) }
                    .distinctUntilChanged()
            }.collectAsState(initial = HomePlaybackSummary())
            val authState by authRepository.authState.collectAsState(initial = AuthState.Initializing)
            val releaseVersionPreference by settingsRepository.releaseVersionPreference.collectAsState(initial = ReleaseVersionPreference.Explicit)
            var currentRoute by remember { mutableStateOf<String?>(null) }

            DisposableEffect(navController) {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    currentRoute = destination.route
                }
                navController.addOnDestinationChangedListener(listener)
                currentRoute = navController.currentDestination?.route
                onDispose { navController.removeOnDestinationChangedListener(listener) }
            }

            LaunchedEffect(routeRequest) {
                routeRequest?.takeIf { it.isNotBlank() }?.let { route ->
                    navController.navigate(route)
                    onRouteRequestConsumed()
                }
            }

            LaunchedEffect(authState, currentRoute) {
                when (authState) {
                    AuthState.UserSignedIn -> if (currentRoute == null || currentRoute == Routes.Onboarding) {
                        navController.navigateAndClear(Routes.Home)
                    }
                    AuthState.Anonymous -> if (currentRoute != Routes.Onboarding) {
                        navController.navigateAndClear(Routes.Onboarding)
                    }
                    AuthState.Initializing -> Unit
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
                navController.navigate(Routes.album(album.id))
            }
            fun openPlaylist(playlist: TidalPlaylist) {
                navController.navigate(Routes.playlist(playlist.id))
            }
            fun openArtist(artist: TidalArtist) {
                navController.navigate(Routes.artist(artist.id))
            }
            fun openArtistAlbums(artist: TidalArtist) {
                navController.navigate(Routes.artistAlbums(artist.id))
            }

            SwipeDismissableNavHost(navController = navController, startDestination = Routes.Onboarding) {
                composable(Routes.Onboarding) {
                    if (authState == AuthState.UserSignedIn) {
                        LaunchedEffect(Unit) { navController.navigateAndClear(Routes.Home) }
                    } else {
                        OnboardingScreen(
                            authRepository = authRepository,
                            onAuthenticated = { navController.navigateAndClear(Routes.Home) },
                        )
                    }
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
                        onOpenArtist = ::openArtist,
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
                        onOpenArtist = ::openArtist,
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
                        initialAlbum = null,
                        onPlayQueue = ::playQueue,
                    )
                }
                composable(Routes.Playlist) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId")?.let(Uri::decode).orEmpty()
                    PlaylistScreen(
                        apiClient = apiClient,
                        playlistId = playlistId,
                        initialPlaylist = null,
                        onPlayQueue = ::playQueue,
                    )
                }
                composable(Routes.Artist) { entry ->
                    val artistId = entry.arguments?.getString("artistId")?.let(Uri::decode).orEmpty()
                    ArtistScreen(
                        apiClient = apiClient,
                        artistId = artistId,
                        initialArtist = null,
                        onPlayTrack = ::playTrack,
                        onPlayQueue = ::playQueue,
                        onOpenAlbum = ::openAlbum,
                        onOpenPlaylist = ::openPlaylist,
                        onOpenArtist = ::openArtist,
                        onOpenAlbums = ::openArtistAlbums,
                        releaseVersionPreference = releaseVersionPreference,
                    )
                }
                composable(Routes.ArtistAlbums) { entry ->
                    val artistId = entry.arguments?.getString("artistId")?.let(Uri::decode).orEmpty()
                    ArtistAlbumsScreen(
                        apiClient = apiClient,
                        artistId = artistId,
                        initialArtist = null,
                        onOpenAlbum = ::openAlbum,
                        releaseVersionPreference = releaseVersionPreference,
                    )
                }
                composable(Routes.Library) {
                    LibraryScreen(
                        apiClient = apiClient,
                        onOpenAlbum = ::openAlbum,
                        onOpenPlaylist = ::openPlaylist,
                        onOpenArtist = ::openArtist,
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
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .rotaryScrollableWithFocus(scrollState)
                .padding(top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HomeWordmark()
            HomePrimaryAction(
                signedIn = signedIn,
                track = track,
                isPlaying = isPlaying,
                onSignedOutClick = { navController.navigate(Routes.Onboarding) },
                onSearchClick = { navController.navigate(Routes.Search) },
                onNowPlayingClick = onNowPlaying,
                onResumeClick = onResume,
            )
            if (signedIn) {
                if (track != null) {
                    HomeListChip(
                        label = "Search",
                        secondaryLabel = "Find music on TIDAL",
                        icon = Icons.Filled.Search,
                        onClick = { navController.navigate(Routes.Search) },
                    )
                }
                HomeListChip(
                    label = "Discover",
                    secondaryLabel = "Personalized mixes",
                    icon = Icons.Filled.Star,
                    onClick = { navController.navigate(Routes.Discover) },
                )
                HomeListChip(
                    label = "Library",
                    secondaryLabel = "Your collection",
                    icon = Icons.Filled.LibraryMusic,
                    onClick = { navController.navigate(Routes.Library) },
                )
                HomeListChip(
                    label = "Downloads",
                    secondaryLabel = "Coming soon",
                    icon = Icons.Filled.Download,
                    enabled = false,
                    onClick = onOffline,
                )
                HomeListChip(
                    label = "Settings",
                    secondaryLabel = "Account & playback",
                    icon = Icons.Filled.Settings,
                    onClick = { navController.navigate(Routes.Settings) },
                )
            }
            HomeFooter()
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
            icon = Icons.AutoMirrored.Filled.Login
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

    HomeRowChip(
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
    HomeRowChip(
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
private fun HomeRowChip(
    label: String,
    secondaryLabel: String,
    icon: ImageVector,
    enabled: Boolean = true,
    iconTint: Color,
    minHeight: Dp,
    surfaceColor: Color,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeHorizontalPadding)
            .heightIn(min = minHeight),
        onClick = onClick,
        enabled = enabled,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = surfaceColor,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
            iconColor = iconTint,
        ),
        icon = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(TidalColors.Black, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = iconTint)
            }
        },
        label = {
            Text(
                text = label,
                color = TidalColors.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = secondaryLabel,
                color = TidalColors.OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    )
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
        .putExtra(PlaybackActions.EXTRA_ALBUM_ID, track.albumId)
        .putExtra(PlaybackActions.EXTRA_ARTIST_ID, track.artistId)
        .putExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN, PlaybackCommandTokenProvider.token(this))
    ContextCompat.startForegroundService(this, intent)
}

private fun Context.resumeTrackPlayback() {
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_RESUME)
        .putExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN, PlaybackCommandTokenProvider.token(this))
    ContextCompat.startForegroundService(this, intent)
}

private fun Context.startQueuePlayback(tracks: List<TidalTrack>, startIndex: Int) {
    val playableTracks = PlaybackQueueStore.playableTracks(tracks)
    if (playableTracks.isEmpty()) return
    val queueId = PlaybackQueueStore.put(playableTracks)
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_PLAY_QUEUE)
        .putExtra(PlaybackActions.EXTRA_QUEUE_ID, queueId)
        .putExtra(PlaybackActions.EXTRA_QUEUE_PAYLOAD, PlaybackQueueStore.payloadFor(playableTracks))
        .putExtra(PlaybackActions.EXTRA_QUEUE_START_INDEX, PlaybackQueueStore.startIndexFor(tracks, startIndex))
        .putExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN, PlaybackCommandTokenProvider.token(this))
    ContextCompat.startForegroundService(this, intent)
}

private fun NavHostController.navigateAndClear(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}




