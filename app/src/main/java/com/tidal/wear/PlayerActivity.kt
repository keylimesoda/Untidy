package com.tidal.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material3.AppScaffold
import com.tidal.wear.playback.NowPlayingViewModel
import com.tidal.wear.ui.player.TidalPlayerScreen
import java.time.LocalTime

private object PlayerRoutes {
    const val Home = "home"

    fun album(albumId: String): String = "album/${Uri.encode(albumId)}"
    fun artist(artistId: String): String = "artist/${Uri.encode(artistId)}"
}

class PlayerActivity : ComponentActivity() {
    private val nowPlayingViewModel: NowPlayingViewModel by viewModels()
    private var isAmbient by mutableStateOf(false)
    private var ambientOffset by mutableStateOf(0 to 0)
    private var deviceHasLowBitAmbient by mutableStateOf(false)
    private var burnInProtectionRequired by mutableStateOf(false)
    private var ambientTime by mutableStateOf(LocalTime.now())

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
            ambientTime = LocalTime.now()
            deviceHasLowBitAmbient = ambientDetails.deviceHasLowBitAmbient
            burnInProtectionRequired = ambientDetails.burnInProtectionRequired
            nowPlayingViewModel.setUiVisibility(visible = true, ambient = true)
        }

        override fun onExitAmbient() {
            isAmbient = false
            ambientTime = LocalTime.now()
            ambientOffset = 0 to 0
            deviceHasLowBitAmbient = false
            burnInProtectionRequired = false
            nowPlayingViewModel.setUiVisibility(visible = true, ambient = false)
        }

        override fun onUpdateAmbient() {
            // Keep the clock state changing even when burn-in protection is off
            // and the pixel offset remains 0,0. This only affects ambient UI;
            // playback position polling is owned by NowPlayingViewModel.
            ambientTime = LocalTime.now()
            ambientOffset = if (burnInProtectionRequired) (-4..4).random() to (-4..4).random() else 0 to 0
        }
    }

    private val ambientObserver by lazy { AmbientLifecycleObserver(this, ambientCallback) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    TidalPlayerScreen(
                        viewModel = nowPlayingViewModel,
                        isAmbient = isAmbient,
                        ambientOffset = ambientOffset,
                        deviceHasLowBitAmbient = deviceHasLowBitAmbient,
                        burnInProtectionRequired = burnInProtectionRequired,
                        ambientTime = ambientTime,
                        onOpenHome = {
                            openMainRoute(PlayerRoutes.Home)
                            finish()
                        },
                        onOpenAlbum = { albumId -> openMainRoute(PlayerRoutes.album(albumId), returnToPlayer = true) },
                        onOpenArtist = { artistId -> openMainRoute(PlayerRoutes.artist(artistId), returnToPlayer = true) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        nowPlayingViewModel.setUiVisibility(visible = true, ambient = isAmbient)
    }

    override fun onStop() {
        nowPlayingViewModel.setUiVisibility(visible = false)
        super.onStop()
    }

    private fun openMainRoute(route: String, returnToPlayer: Boolean = false) {
        // Wear OS recents treats CLEAR_TOP-style task surgery poorly. MainActivity is
        // singleTop with an empty taskAffinity, so REORDER_TO_FRONT reuses the
        // existing authenticated task and still delivers the route through onNewIntent.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_ROUTE, route)
                .putExtra(MainActivity.EXTRA_RETURN_TO_PLAYER, returnToPlayer),
        )
    }
}
