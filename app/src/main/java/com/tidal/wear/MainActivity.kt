package com.tidal.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tidal.wear.core.playback.offline.DownloadedTrackSummary
import com.tidal.wear.core.playback.offline.OfflineDownloadScheduler
import com.tidal.wear.core.playback.offline.OfflineTrackDownloader
import com.tidal.wear.core.playback.offline.isOfflineTrackDownloaded
import com.tidal.wear.core.playback.offline.readOfflineDownloadedTracks
import com.tidal.wear.playback.NowPlayingStateHolder
import com.tidal.wear.recent.RecentItemType
import com.tidal.wear.recent.RecentRepository
import com.tidal.wear.ui.recent.RecentScreen
import com.tidal.wear.ui.recent.TrackContextScreen
import com.tidal.wear.ui.album.AlbumScreen
import com.tidal.wear.ui.artist.ArtistAlbumsScreen
import com.tidal.wear.ui.artist.ArtistScreen
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.discover.DiscoverScreen
import com.tidal.wear.ui.downloads.DownloadsScreen
import com.tidal.wear.ui.library.LibraryScreen
import com.tidal.wear.ui.home.HomeReadyItem
import com.tidal.wear.ui.home.HomeShelfContent
import com.tidal.wear.ui.home.HomeExploreDestination
import com.tidal.wear.ui.home.homeExploreDestinations
import com.tidal.wear.ui.home.selectHomeShelfContent
import com.tidal.wear.ui.offline.isNetworkAvailable
import com.tidal.wear.ui.onboarding.OnboardingScreen
import com.tidal.wear.ui.playlist.PlaylistScreen
import com.tidal.wear.ui.search.SearchScreen
import com.tidal.wear.ui.settings.SettingsRepository
import com.tidal.wear.ui.settings.SettingsScreen
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val HomePrimarySurface = Color(0xFF1A1A1A)
private val HomeSecondarySurface = Color(0xFF1A1A1A)
private val HomeDisabledContent = Color(0x80FFFFFF)
private val HomeHorizontalPadding = 10.dp

internal data class MainRouteRequest(
    val route: String,
    val returnToPlayer: Boolean,
)

internal data class ExternalRoutePlan(
    val route: String,
    val clearBackStack: Boolean,
    val returnToPlayer: Boolean,
)

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var routeRequest by mutableStateOf<MainRouteRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeRequest = intent.toMainRouteRequest()
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
        routeRequest = intent.toMainRouteRequest()
    }

    companion object {
        const val EXTRA_ROUTE = "com.tidal.wear.extra.ROUTE"
        const val EXTRA_RETURN_TO_PLAYER = "com.tidal.wear.extra.RETURN_TO_PLAYER"
        private const val NOTIFICATION_PERMISSION_PREFS = "untidy_notification_permission"
        private const val KEY_NOTIFICATION_PERMISSION_ASKED = "asked"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences(NOTIFICATION_PERMISSION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)) return
        // Record the prompt before launching so a recreated Activity cannot ask again.
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Discover = "discover"
    const val Search = "search"
    const val Downloads = "downloads"
    const val Recent = "recent"
    const val TrackContext = "recent/track/{trackId}"
    const val Album = "album/{albumId}"
    const val Playlist = "playlist/{playlistId}"
    const val Artist = "artist/{artistId}"
    const val ArtistAlbums = "artist/{artistId}/albums"
    const val Library = "library"
    const val Settings = "settings"

    fun trackContext(trackId: String): String = "recent/track/${Uri.encode(trackId)}"
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
    routeRequest: MainRouteRequest? = null,
    onRouteRequestConsumed: () -> Unit = {},
) {
    MaterialTheme {
        AppScaffold(timeText = {}) {
            val navController = rememberSwipeDismissableNavController()
            val context = LocalContext.current
            val authRepository = remember(context) { TidalAuthRepositoryProvider.get(context.applicationContext) }
            val settingsRepository = remember(context) { SettingsRepository(context.applicationContext) }
            val recentRepository = remember(context) { RecentRepository(context.applicationContext) }
            val apiClient = remember(authRepository) { TidalApiClient(authRepository) }
            val appContext = remember(context) { context.applicationContext }
            val offlineDownloader = remember(authRepository, appContext) { OfflineTrackDownloader(appContext, authRepository) }
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
            val hasUsableSession by authRepository.isAuthenticated.collectAsState(initial = true)
            val releaseVersionPreference by settingsRepository.releaseVersionPreference.collectAsState(initial = ReleaseVersionPreference.Explicit)
            val recentItems by recentRepository.items.collectAsState(initial = emptyList())
            // Reading the offline shelf scans SharedPreferences and sorts every downloaded
            // track, so keep it out of the composition body and load it on IO when Home is shown.
            var downloadedTracks by remember { mutableStateOf(emptyList<DownloadedTrackSummary>()) }
            var currentRoute by remember { mutableStateOf<String?>(null) }
            // Re-read when a download starts/finishes so the shelf updates in place
            // without the raw queue-emission storm the old composition read caused.
            val homeDownloadScheduler = remember(appContext) { OfflineDownloadScheduler(appContext) }
            val homeActiveDownloads by homeDownloadScheduler.observeActiveTracks()
                .collectAsState(initial = emptyList())
            val homeDownloadTick = homeActiveDownloads.size
            LaunchedEffect(currentRoute, homeDownloadTick) {
                if (currentRoute == Routes.Home) {
                    downloadedTracks = withContext(Dispatchers.IO) {
                        appContext.readOfflineDownloadedTracks()
                    }
                }
            }
            val homeShelfContent = remember(downloadedTracks, recentItems) {
                selectHomeShelfContent(
                    downloadedTracks = downloadedTracks,
                    recentItems = recentItems,
                )
            }
            var currentBackStackEntryId by remember { mutableStateOf<String?>(null) }
            // A route is only a destination pattern, so it cannot distinguish two artist
            // (or album) entries. Keep the concrete entry created for a player handoff.
            var returnToPlayerRootEntryId by rememberSaveable { mutableStateOf<String?>(null) }

            DisposableEffect(navController) {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    currentRoute = destination.route
                    currentBackStackEntryId = navController.currentBackStackEntry?.id
                }
                navController.addOnDestinationChangedListener(listener)
                currentRoute = navController.currentDestination?.route
                currentBackStackEntryId = navController.currentBackStackEntry?.id
                onDispose { navController.removeOnDestinationChangedListener(listener) }
            }

            LaunchedEffect(routeRequest, authState) {
                val request = routeRequest ?: return@LaunchedEffect
                if (request.route.isBlank() || authState != AuthState.UserSignedIn) return@LaunchedEffect
                val plan = planExternalRoute(request.route, request.returnToPlayer)
                if (plan.clearBackStack) {
                    navController.navigateAndClear(plan.route)
                } else {
                    navController.navigate(plan.route)
                }
                // Destination callbacks are not guaranteed to update Compose state before
                // consuming the request. Publish the synchronous destination here so the
                // signed-in redirect cannot mistake this transition for an unhandled startup.
                currentRoute = navController.currentDestination?.route
                returnToPlayerRootEntryId = plan.returnToPlayerRootEntryId(navController.currentBackStackEntry?.id)
                onRouteRequestConsumed()
            }

            LaunchedEffect(authState, currentRoute, routeRequest, hasUsableSession) {
                when (authState) {
                    AuthState.UserSignedIn -> if (shouldNavigateSignedInHome(routeRequest != null, currentRoute)) {
                        navController.navigateAndClear(Routes.Home)
                    }
                    AuthState.Anonymous -> if (currentRoute != Routes.Onboarding) {
                        navController.navigateAndClear(Routes.Onboarding)
                    }
                    // A signed-in session that died server-side (refresh token revoked
                    // or aged out) clears prefs and lands here; without this the user
                    // keeps seeing dead content panes instead of a fresh sign-in code.
                    AuthState.Initializing -> if (!hasUsableSession &&
                        currentRoute != null && currentRoute != Routes.Onboarding
                    ) {
                        navController.navigateAndClear(Routes.Onboarding)
                    }
                }
            }

            fun openPlayer() = context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            fun onPlaybackRejected() {
                Toast.makeText(context, "Not downloaded · connect to stream this track", Toast.LENGTH_LONG).show()
            }
            fun playTrack(track: TidalTrack) {
                val started = context.startTrackPlayback(track)
                if (!started) {
                    onPlaybackRejected()
                    return
                }
                recentRepository.recordTrack(track)
                openPlayer()
            }
            fun playQueue(tracks: List<TidalTrack>, startIndex: Int) {
                val started = context.startQueuePlayback(tracks, startIndex)
                if (!started) {
                    onPlaybackRejected()
                    return
                }
                tracks.getOrNull(startIndex)?.let(recentRepository::recordTrack)
                openPlayer()
            }
            fun playAlbum(album: TidalAlbum, tracks: List<TidalTrack>, startIndex: Int) {
                val started = context.startQueuePlayback(tracks, startIndex)
                if (!started) {
                    onPlaybackRejected()
                    return
                }
                recentRepository.recordAlbum(album)
                openPlayer()
            }
            fun playPlaylist(playlist: TidalPlaylist, tracks: List<TidalTrack>, startIndex: Int) {
                val started = context.startQueuePlayback(tracks, startIndex)
                if (!started) {
                    onPlaybackRejected()
                    return
                }
                recentRepository.recordPlaylist(playlist)
                openPlayer()
            }
            fun openAlbumId(albumId: String) {
                navController.navigate(Routes.album(albumId))
            }
            fun openPlaylistId(playlistId: String) {
                navController.navigate(Routes.playlist(playlistId))
            }
            fun openArtistId(artistId: String) {
                navController.navigate(Routes.artist(artistId))
            }
            fun openAlbum(album: TidalAlbum) = openAlbumId(album.id)
            fun openPlaylist(playlist: TidalPlaylist) = openPlaylistId(playlist.id)
            fun openArtist(artist: TidalArtist) = openArtistId(artist.id)
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
                        shelfContent = homeShelfContent,
                        onNowPlaying = ::openPlayer,
                        onResume = {
                            homePlayback.track?.let {
                                context.resumeTrackPlayback()
                                openPlayer()
                            } ?: navController.navigate(Routes.Search)
                        },
                        onOffline = { navController.navigate(Routes.Downloads) },
                        onPlayTrack = ::playTrack,
                        onOpenRecentTrack = { trackId -> navController.navigate(Routes.trackContext(trackId)) },
                        onOpenAlbum = ::openAlbumId,
                        onOpenPlaylist = ::openPlaylistId,
                    )
                }
                composable(Routes.Downloads) {
                    DownloadsScreen(
                        context = appContext,
                        onPlayTrack = ::playTrack,
                    )
                }
                composable(Routes.Recent) {
                    RecentScreen(
                        items = recentItems,
                        onOpenTrack = { item -> navController.navigate(Routes.trackContext(item.id)) },
                        onOpenAlbum = ::openAlbumId,
                        onOpenPlaylist = ::openPlaylistId,
                    )
                }
                composable(Routes.TrackContext) { entry ->
                    val trackId = entry.arguments?.getString("trackId")?.let(Uri::decode).orEmpty()
                    TrackContextScreen(
                        apiClient = apiClient,
                        offlineDownloader = offlineDownloader,
                        initialRecent = recentItems.firstOrNull { it.type == RecentItemType.Track && it.id == trackId },
                        trackId = trackId,
                        onPlayTrack = ::playTrack,
                        onOpenAlbum = ::openAlbumId,
                        onOpenArtist = ::openArtistId,
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
                        onOpenDownloads = { navController.navigate(Routes.Downloads) },
                    )
                }
                composable(Routes.Search) {
                    SearchScreen(
                        apiClient = apiClient,
                        onPlayTrack = ::playTrack,
                        onOpenAlbum = ::openAlbum,
                        onOpenPlaylist = ::openPlaylist,
                        onOpenArtist = ::openArtist,
                        onOpenDownloads = { navController.navigate(Routes.Downloads) },
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
                        offlineDownloader = offlineDownloader,
                        albumId = albumId,
                        initialAlbum = null,
                        onPlayAlbum = ::playAlbum,
                    )
                }
                composable(Routes.Playlist) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId")?.let(Uri::decode).orEmpty()
                    PlaylistScreen(
                        apiClient = apiClient,
                        offlineDownloader = offlineDownloader,
                        playlistId = playlistId,
                        initialPlaylist = null,
                        onPlayPlaylist = ::playPlaylist,
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
                        onOpenDownloads = { navController.navigate(Routes.Downloads) },
                    )
                }
                composable(Routes.Settings) {
                    SettingsScreen(
                        authRepository = authRepository,
                        onOpenDownloads = { navController.navigate(Routes.Downloads) },
                        onSignedOut = {
                            recentRepository.clear()
                            navController.navigateAndClear(Routes.Onboarding)
                        },
                    )
                }
            }
            BackHandler(enabled = isReturnToPlayerRoot(returnToPlayerRootEntryId, currentBackStackEntryId)) {
                returnToPlayerRootEntryId = null
                (context as? MainActivity)?.finish()
            }
        }
    }
}

internal fun shouldNavigateSignedInHome(hasPendingRouteRequest: Boolean, currentRoute: String?): Boolean =
    !hasPendingRouteRequest && (currentRoute == null || currentRoute == Routes.Onboarding)

private fun Intent.toMainRouteRequest(): MainRouteRequest? =
    getStringExtra(MainActivity.EXTRA_ROUTE)
        ?.takeIf(String::isNotBlank)
        ?.let { route ->
            MainRouteRequest(
                route = route,
                returnToPlayer = getBooleanExtra(MainActivity.EXTRA_RETURN_TO_PLAYER, false),
            )
        }

internal fun planExternalRoute(route: String, returnToPlayer: Boolean): ExternalRoutePlan =
    ExternalRoutePlan(
        route = route,
        clearBackStack = route == Routes.Home || returnToPlayer,
        returnToPlayer = returnToPlayer,
    )

internal fun ExternalRoutePlan.returnToPlayerRootEntryId(currentEntryId: String?): String? =
    currentEntryId.takeIf { returnToPlayer }

internal fun isReturnToPlayerRoot(rootEntryId: String?, currentEntryId: String?): Boolean =
    rootEntryId != null && rootEntryId == currentEntryId

@Composable
private fun HomeScreen(
    navController: NavHostController,
    authState: AuthState,
    track: TidalTrack?,
    isPlaying: Boolean,
    shelfContent: HomeShelfContent,
    onNowPlaying: () -> Unit,
    onResume: () -> Unit,
    onOffline: () -> Unit,
    onPlayTrack: (TidalTrack) -> Unit,
    onOpenRecentTrack: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val signedIn = authState == AuthState.UserSignedIn
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .rotaryScrollableWithFocus(scrollState)
                .padding(top = WearListPadding.RoundScreenTop, bottom = WearListPadding.RoundScreenBottom),
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
                val offlineReadyItems = shelfContent.offlineItems
                val recentContextItems = shelfContent.recentItems
                if (offlineReadyItems.isNotEmpty()) {
                    HomeSectionLabel("Offline ready")
                    offlineReadyItems.forEach { readyItem ->
                        HomeListChip(
                            label = readyItem.title,
                            secondaryLabel = "Downloaded · tap to play",
                            icon = Icons.Filled.PlayArrow,
                            onClick = { onPlayTrack(readyItem.track) },
                        )
                    }
                }
                HomeSectionLabel("Recent")
                if (recentContextItems.isNotEmpty()) {
                    recentContextItems.forEach { readyItem ->
                        when (readyItem) {
                            is HomeReadyItem.Track -> HomeListChip(
                                label = readyItem.title,
                                secondaryLabel = "Recent track · ${readyItem.subtitle.ifBlank { "Open context" }}",
                                icon = Icons.Filled.PlayArrow,
                                onClick = { onOpenRecentTrack(readyItem.id) },
                            )
                            is HomeReadyItem.Album -> HomeListChip(
                                label = readyItem.title,
                                secondaryLabel = "Recent album · ${readyItem.subtitle.ifBlank { "Open album" }}",
                                icon = Icons.Filled.Album,
                                onClick = { onOpenAlbum(readyItem.id) },
                            )
                            is HomeReadyItem.Playlist -> HomeListChip(
                                label = readyItem.title,
                                secondaryLabel = "Recent playlist · ${readyItem.subtitle.ifBlank { "Open playlist" }}",
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                onClick = { onOpenPlaylist(readyItem.id) },
                            )
                        }
                    }
                }
                HomeListChip(
                    label = "See all",
                    secondaryLabel = "Open full recent history",
                    icon = Icons.Filled.History,
                    onClick = { navController.navigate(Routes.Recent) },
                )
                HomeSectionLabel("Explore")
                homeExploreDestinations(searchIsPrimary = track == null).forEach { destination ->
                    HomeListChip(
                        label = destination.label,
                        secondaryLabel = destination.secondaryLabel,
                        icon = when (destination) {
                            HomeExploreDestination.Search -> Icons.Filled.Search
                            HomeExploreDestination.Library -> Icons.Filled.LibraryMusic
                            HomeExploreDestination.Discover -> Icons.Filled.Star
                            HomeExploreDestination.OnThisWatch -> Icons.Filled.Download
                            HomeExploreDestination.Settings -> Icons.Filled.Settings
                        },
                        onClick = when (destination) {
                            HomeExploreDestination.Search -> ({ navController.navigate(Routes.Search) })
                            HomeExploreDestination.Library -> ({ navController.navigate(Routes.Library) })
                            HomeExploreDestination.Discover -> ({ navController.navigate(Routes.Discover) })
                            HomeExploreDestination.OnThisWatch -> onOffline
                            HomeExploreDestination.Settings -> ({ navController.navigate(Routes.Settings) })
                        },
                    )
                }
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
private fun HomeSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HomeHorizontalPadding, top = 8.dp, end = HomeHorizontalPadding, bottom = 2.dp),
        color = TidalColors.OnSurfaceMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
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

private fun Context.startTrackPlayback(track: TidalTrack): Boolean {
    if (!canStartLiveOrDownloadedPlayback(track.id)) return false
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
    return true
}

private fun Context.resumeTrackPlayback() {
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_RESUME)
        .putExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN, PlaybackCommandTokenProvider.token(this))
    ContextCompat.startForegroundService(this, intent)
}

private fun Context.startQueuePlayback(tracks: List<TidalTrack>, startIndex: Int): Boolean {
    val playableTracks = PlaybackQueueStore.playableTracks(tracks)
        .filter { canStartLiveOrDownloadedPlayback(it.id) }
    if (playableTracks.isEmpty()) return false
    val requestedTrackId = tracks.getOrNull(startIndex)?.id
    val playbackStartIndex = requestedTrackId
        ?.let { id -> playableTracks.indexOfFirst { it.id == id } }
        ?.takeIf { it >= 0 }
        ?: 0
    val queueId = PlaybackQueueStore.put(playableTracks)
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_PLAY_QUEUE)
        .putExtra(PlaybackActions.EXTRA_QUEUE_ID, queueId)
        .putExtra(PlaybackActions.EXTRA_QUEUE_PAYLOAD, PlaybackQueueStore.payloadFor(playableTracks))
        .putExtra(PlaybackActions.EXTRA_QUEUE_START_INDEX, playbackStartIndex)
        .putExtra(PlaybackActions.EXTRA_APP_COMMAND_TOKEN, PlaybackCommandTokenProvider.token(this))
    ContextCompat.startForegroundService(this, intent)
    return true
}

private fun Context.canStartLiveOrDownloadedPlayback(trackId: String): Boolean =
    isNetworkAvailable() || isOfflineTrackDownloaded(trackId)

private fun NavHostController.navigateAndClear(route: String) {
    navigate(route) {
        // The graph entry remains in the back stack even after its start destination
        // has been replaced. Clearing to it avoids targeting a removed Onboarding entry.
        popUpTo(graph.id) { inclusive = false }
        launchSingleTop = true
    }
}




