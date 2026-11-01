package com.tidal.wear.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors

@Composable
internal fun AudioOutputPicker(
    requestedOutputId: Int?,
    onOutputSelected: (Int?) -> Unit,
    onConnectBluetooth: () -> Unit,
    onBack: () -> Unit,
) {
    val outputState = rememberAudioOutputOptions(requestedOutputId)
    val outputs = outputState.outputs
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val view = LocalView.current
    ScalingLazyColumn(
        state = listState,
        autoCentering = null,
        modifier = Modifier.fillMaxSize().background(TidalColors.Black)
            .rotaryScrollableWithFocus(listState),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                "Play on",
                color = TidalColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (outputs.isEmpty()) {
            item {
                Text(
                    "No audio outputs available",
                    color = TidalColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        outputs.forEach { output ->
            item(key = output.id) {
                ActionRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = output.label,
                    rightIndicator = if (output.selected) Icons.Filled.Check else null,
                    iconTint = if (output.selected) TidalColors.Cyan else TidalColors.White,
                    selected = output.selected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onOutputSelected(output.id)
                    },
                )
            }
        }
        if (!outputState.bluetoothConnected) {
            item(key = "connect-bluetooth-caption") {
                Text(
                    "Paired headphones must be connected before they can play",
                    color = TidalColors.OnSurfaceMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item(key = "connect-bluetooth") {
                ActionRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Connect Bluetooth headphones",
                    rightIndicator = null,
                    iconTint = TidalColors.Cyan,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onConnectBluetooth()
                    },
                )
            }
        }
        item {
            ActionRow(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back",
                rightIndicator = null,
                iconTint = TidalColors.OnSurfaceMuted,
                onClick = onBack,
            )
        }
    }
}
