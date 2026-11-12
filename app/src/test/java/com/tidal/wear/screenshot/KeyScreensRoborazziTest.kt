package com.tidal.wear.screenshot

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.github.takahirom.roborazzi.captureRoboImage
import com.tidal.sdk.auth.CredentialsProvider
import com.tidal.wear.HomeScreen
import com.tidal.wear.core.auth.AuthState
import com.tidal.wear.core.auth.DeviceAuthSession
import com.tidal.wear.core.auth.TidalAccountInfo
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.ReleaseVersionPreference
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.playback.NowPlayingUiState
import com.tidal.wear.recent.RecentItem
import com.tidal.wear.recent.RecentItemType
import com.tidal.wear.ui.album.AlbumScreenContent
import com.tidal.wear.ui.home.HomeReadyItem
import com.tidal.wear.ui.home.HomeShelfContent
import com.tidal.wear.ui.player.DownloadState
import com.tidal.wear.ui.player.MoreActionsHintPreference
import com.tidal.wear.ui.player.PlayerNavigationState
import com.tidal.wear.ui.player.TidalPlayerActions
import com.tidal.wear.ui.player.TidalPlayerNonAmbient
import com.tidal.wear.ui.settings.SettingsScreenContent
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Seeded Roborazzi goldens for the four highest-risk Wear screens.
 *
 * Each screen is rendered at 216dp x 216dp @ 320dpi (= 432 x 432 px) in both
 * round and square screen-shape configurations. Round and square share the same
 * pixel viewport so the goldens isolate shape/qualifier-driven safe-area drift.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class KeyScreensRoborazziTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, round: Boolean, content: @Composable () -> Unit) {
        check(composeRule.activity.resources.configuration.isScreenRound == round) {
            "Robolectric qualifiers did not apply the requested screen shape for $name"
        }
        composeRule.setContent {
            MaterialTheme {
                // Round watches clip the app to a circle at the display layer. Simulate that
                // mask so round goldens expose corner/edge content that a square viewport hides.
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(if (round) BezelColor else Color.Transparent),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (round) Modifier.clip(CircleShape) else Modifier),
                    ) {
                        AppScaffold(timeText = {}) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { content() }
                        }
                    }
                }
            }
        }
        onView(isRoot()).captureRoboImage("images/$name.png")
    }

    // ---- Home shelf area -------------------------------------------------

    @Test
    @Config(qualifiers = ROUND)
    fun homeRound() = capture("home_round", round = true, content = ::HomeShelfPreview)

    @Test
    @Config(qualifiers = SQUARE)
    fun homeSquare() = capture("home_square", round = false, content = ::HomeShelfPreview)

    @Composable
    private fun HomeShelfPreview() {
        HomeScreen(
            navController = rememberSwipeDismissableNavController(),
            authState = AuthState.UserSignedIn,
            track = SampleTrack,
            isPlaying = false,
            shelfContent = SampleShelf,
            onNowPlaying = {},
            onResume = {},
            onOffline = {},
            onPlayTrack = {},
            onOpenRecentTrack = {},
            onOpenAlbum = {},
            onOpenPlaylist = {},
        )
    }

    // ---- Now playing -----------------------------------------------------

    @Test
    @Config(qualifiers = ROUND)
    fun playerRound() = capture("player_round", round = true, content = ::PlayerPreview)

    @Test
    @Config(qualifiers = SQUARE)
    fun playerSquare() = capture("player_square", round = false, content = ::PlayerPreview)

    @Composable
    private fun PlayerPreview() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MoreActionsHintPreference.markShown(context)
        TidalPlayerNonAmbient(
            state = SamplePlayerState,
            albumArt = null,
            accent = TidalColors.Cyan,
            actions = NoOpPlayerActions,
            apiClient = TidalApiClient(FakeAuthRepository()),
            downloadState = DownloadState.NotDownloaded,
            onDownload = {},
            onRemoveDownload = { null },
            onOpenHome = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            navigation = PlayerNavigationState(),
            playlistOperationScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    // ---- Settings --------------------------------------------------------

    @Test
    @Config(qualifiers = ROUND)
    fun settingsRound() = capture("settings_round", round = true, content = ::SettingsPreview)

    @Test
    @Config(qualifiers = SQUARE)
    fun settingsSquare() = capture("settings_square", round = false, content = ::SettingsPreview)

    @Composable
    private fun SettingsPreview() {
        SettingsScreenContent(
            authState = AuthState.UserSignedIn,
            accountInfo = SampleAccountInfo,
            preset = AudioPreset.High,
            releaseVersionPreference = ReleaseVersionPreference.Explicit,
            downloadOverLte = false,
            downloadedTracks = emptyList(),
            downloadsStorageBytes = 0L,
            onOpenDownloads = {},
            onPresetSelected = {},
            onReleaseVersionSelected = {},
            onDownloadOverLteChanged = {},
            onSignOut = {},
            onRemoveAllDownloads = {},
        )
    }

    // ---- Album -----------------------------------------------------------

    @Test
    @Config(qualifiers = ROUND)
    fun albumRound() = capture("album_round", round = true, content = ::AlbumPreview)

    @Test
    @Config(qualifiers = SQUARE)
    fun albumSquare() = capture("album_square", round = false, content = ::AlbumPreview)

    @Composable
    private fun AlbumPreview() {
        AlbumScreenContent(
            title = "When We All Fall Asleep, Where Do We Go?",
            artwork = null,
            accent = TidalColors.Cyan,
            liked = true,
            likeLoading = false,
            onLike = {},
            tracks = SampleTracks,
            loading = false,
            error = null,
            onRetry = {},
            offlineLabel = "Download album",
            offlineDetail = "3 tracks · 12 MB",
            onDownload = {},
            onPlay = {},
            onShuffle = {},
            onTrackClick = {},
        )
    }

    private companion object {
        const val ROUND = "w216dp-h216dp-round-320dpi"
        const val SQUARE = "w216dp-h216dp-notround-320dpi"
        val BezelColor = Color(0xFF404040)

        val SampleTrack = TidalTrack(
            id = "fixture-preview-track",
            title = "bad guy",
            artist = "Billie Eilish",
            album = "When We All Fall Asleep, Where Do We Go?",
            durationMs = 194_000L,
        )

        val SampleTracks = listOf(
            SampleTrack,
            SampleTrack.copy(id = "fixture-preview-track-2", title = "xanny"),
            SampleTrack.copy(id = "fixture-preview-track-3", title = "you should see me in a crown"),
        )

        val SampleShelf = HomeShelfContent(
            offlineItems = listOf(HomeReadyItem.Track(SampleTrack, downloaded = true)),
            recentItems = listOf(
                HomeReadyItem.Album(
                    RecentItem(
                        type = RecentItemType.Album,
                        id = "fixture-album",
                        title = "When We All Fall Asleep",
                        subtitle = "Billie Eilish",
                        artworkUrl = null,
                        lastPlayedAt = 0L,
                    ),
                ),
                HomeReadyItem.Playlist(
                    RecentItem(
                        type = RecentItemType.Playlist,
                        id = "fixture-playlist",
                        title = "Late night",
                        subtitle = "24 tracks",
                        artworkUrl = null,
                        lastPlayedAt = 0L,
                    ),
                ),
            ),
        )

        val SamplePlayerState = NowPlayingUiState(
            track = SampleTrack,
            isPlaying = true,
            positionMs = 62_000L,
            durationMs = 194_000L,
            volume = 7,
            maxVolume = 15,
            isLoading = false,
        )

        val SampleAccountInfo = TidalAccountInfo(
            userId = "123456789",
            tokenClientId = "preview-client",
            scopes = setOf("user.read", "collection.read", "playback"),
            expiresAtEpochSeconds = 0L,
        )

        val NoOpPlayerActions = object : TidalPlayerActions {
            override fun togglePlayPause() = Unit
            override fun seekToPrevious() = Unit
            override fun seekToNext() = Unit
            override fun jumpToQueueIndex(index: Int) = Unit
            override fun setVolumeFraction(fraction: Float) = Unit
            override fun setPreferredAudioOutput(deviceId: Int?) = Unit
            override fun retryPlayback() = Unit
        }
    }
}

private class FakeAuthRepository : TidalAuthRepository {
    override val isAuthenticated: Flow<Boolean> = MutableStateFlow(true)
    override val authState: Flow<AuthState> = MutableStateFlow(AuthState.UserSignedIn)
    override val accountInfo: Flow<TidalAccountInfo?> = MutableStateFlow(
        TidalAccountInfo(
            userId = "123456789",
            tokenClientId = "preview-client",
            scopes = setOf("user.read", "collection.read", "playback"),
            expiresAtEpochSeconds = 0L,
        ),
    )
    override val credentialsProvider: CredentialsProvider
        get() = error("credentialsProvider is not used by screenshot rendering")

    override suspend fun ensureClientCredentialsToken(): String? = null
    override suspend fun getAccessToken(): String? = null
    override suspend fun getClientIdForApi(): String = "preview-client"
    override suspend fun startDeviceAuth(): DeviceAuthSession = error("not used")
    override suspend fun awaitAuthCompletion(session: DeviceAuthSession): Result<Unit> =
        Result.failure(IllegalStateException("not used"))
    override suspend fun signOut() = Unit
}
