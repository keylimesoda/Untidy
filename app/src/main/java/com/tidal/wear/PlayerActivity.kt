package com.tidal.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material3.AppScaffold
import com.tidal.wear.playback.NowPlayingViewModel
import com.tidal.wear.ui.player.TidalPlayerScreen

class PlayerActivity : ComponentActivity() {
    private var isAmbient by mutableStateOf(false)
    private var ambientOffset by mutableStateOf(0 to 0)
    private var deviceHasLowBitAmbient by mutableStateOf(false)
    private var burnInProtectionRequired by mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
            deviceHasLowBitAmbient = ambientDetails.deviceHasLowBitAmbient
            burnInProtectionRequired = ambientDetails.burnInProtectionRequired
        }

        override fun onExitAmbient() {
            isAmbient = false
            ambientOffset = 0 to 0
            deviceHasLowBitAmbient = false
            burnInProtectionRequired = false
        }

        override fun onUpdateAmbient() {
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
                        viewModel = viewModel<NowPlayingViewModel>(),
                        isAmbient = isAmbient,
                        ambientOffset = ambientOffset,
                        deviceHasLowBitAmbient = deviceHasLowBitAmbient,
                        burnInProtectionRequired = burnInProtectionRequired,
                    )
                }
            }
        }
    }
}
