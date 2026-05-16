package com.tidal.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.playback.PlaybackActions
import com.tidal.wear.core.playback.TidalMediaService
import com.tidal.wear.playback.NowPlayingViewModel
import com.tidal.wear.ui.components.SecondaryChip
import com.tidal.wear.ui.foryou.ForYouScreen
import com.tidal.wear.ui.library.LibraryScreen
import com.tidal.wear.ui.onboarding.OnboardingScreen
import com.tidal.wear.ui.search.SearchScreen
import com.tidal.wear.ui.settings.SettingsScreen
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    const val ForYou = "for-you"
    const val Search = "search"
    const val Library = "library"
    const val Settings = "settings"
}

@Composable
private fun TidalWearApp() {
    MaterialTheme {
        AppScaffold(timeText = {}) {
            val navController = rememberSwipeDismissableNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val authRepository = remember(context) { TidalAuthRepositoryProvider.get(context.applicationContext) }
            val apiClient = remember(authRepository) { TidalApiClient(authRepository) }
            val nowPlayingViewModel = viewModel<NowPlayingViewModel>()
            val nowPlaying by nowPlayingViewModel.state.collectAsState()
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
            fun playQuery(query: String) {
                scope.launch {
                    val track = withContext(Dispatchers.IO) { runCatching { apiClient.search(query).tracks.firstOrNull() }.getOrNull() }
                        ?: fixtureTrack()
                    playTrack(track)
                }
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
                        track = nowPlaying.track,
                        connectivityLabel = context.connectivityLabel(),
                        onResume = { nowPlaying.track?.let(::playTrack) ?: playQuery("Daft Punk") },
                        onOffline = { Toast.makeText(context, "Offline coming soon", Toast.LENGTH_SHORT).show() },
                    )
                }
                composable(Routes.ForYou) { ForYouScreen(onPlayQuery = ::playQuery) }
                composable(Routes.Search) { SearchScreen(apiClient = apiClient, onPlayTrack = ::playTrack) }
                composable(Routes.Library) { LibraryScreen() }
                composable(Routes.Settings) { SettingsScreen(authRepository = authRepository, onSignedOut = { navController.navigateAndClear(Routes.Home) }) }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    navController: NavHostController,
    track: TidalTrack?,
    connectivityLabel: String,
    onResume: () -> Unit,
    onOffline: () -> Unit,
) {
    val hasLastPlayed = track != null
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Untidy", color = TidalColors.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.width(6.dp))
            Text(connectivityLabel, color = TidalColors.OnSurfaceMuted, fontSize = 12.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(TidalColors.Cyan, CircleShape)
                    .clickable(onClick = onResume),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(28.dp), tint = TidalColors.Black)
            }
            Text(
                if (hasLastPlayed) "Resume" else "Start",
                color = TidalColors.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (hasLastPlayed) {
                Text(
                    "${track?.title.orEmpty()} · ${track?.artist.orEmpty()}",
                    color = TidalColors.OnSurfaceMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 94.dp, start = 4.dp, end = 4.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(-10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryChip("For You", Icons.Filled.Star, onClick = { navController.navigate(Routes.ForYou) })
                SecondaryChip("Search", Icons.Filled.Search, onClick = { navController.navigate(Routes.Search) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryChip("Library", Icons.Filled.LibraryMusic, onClick = { navController.navigate(Routes.Library) })
                SecondaryChip("Offline", Icons.Filled.Download, onClick = onOffline)
            }
        }
    }
}

private fun Context.startTrackPlayback(track: TidalTrack) {
    val intent = Intent(this, TidalMediaService::class.java)
        .setAction(PlaybackActions.ACTION_PLAY_TRACK)
        .putExtra(PlaybackActions.EXTRA_TRACK_ID, track.id)
        .putExtra(PlaybackActions.EXTRA_TITLE, track.title)
        .putExtra(PlaybackActions.EXTRA_ARTIST, track.artist)
        .putExtra(PlaybackActions.EXTRA_ALBUM, track.album)
        .putExtra(PlaybackActions.EXTRA_ARTWORK_URL, track.artworkUrl)
    ContextCompat.startForegroundService(this, intent)
}

private fun fixtureTrack() = TidalTrack(
    id = "fixture-run-01",
    title = "TIDAL Preview",
    artist = "Wear OS",
    album = "Fixture",
    durationMs = 30_000L,
)

private fun Context.connectivityLabel(): String {
    val manager = getSystemService(ConnectivityManager::class.java) ?: return "Offline"
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "Offline"
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "LTE"
        else -> "Online"
    }
}

private fun NavHostController.navigateAndClear(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}



