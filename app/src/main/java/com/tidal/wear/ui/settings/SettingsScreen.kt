package com.tidal.wear.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.ui.components.tidalSecondaryChipColors
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    authRepository: TidalAuthRepository,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository(context.applicationContext) }
    val preset by repository.preset.collectAsState(initial = AudioPreset.BatterySaver)
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Streaming quality",
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = TidalColors.White,
                        fontWeight = FontWeight.Black,
                    )
                }
                item {
                    PresetChip("Battery Saver", AudioPreset.BatterySaver, preset) {
                        scope.launch { repository.setPreset(AudioPreset.BatterySaver) }
                    }
                }
                item {
                    PresetChip("Balanced", AudioPreset.Balanced, preset) {
                        scope.launch { repository.setPreset(AudioPreset.Balanced) }
                    }
                }
                item {
                    PresetChip("High", AudioPreset.High, preset) {
                        scope.launch { repository.setPreset(AudioPreset.High) }
                    }
                }
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        onClick = { },
                        icon = {
                            Box(
                                    Modifier
                                     .size(10.dp)
                                     .clip(CircleShape)
                                     .background(if (isAuthenticated) TidalColors.Cyan else TidalColors.OnSurfaceMuted),
                             )
                         },
                         label = { Text("Catalog access") },
                         secondaryLabel = { Text(if (isAuthenticated) "ready" else "connecting") },
                         colors = tidalSecondaryChipColors(),
                     )
                 }
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        onClick = {
                            scope.launch {
                                authRepository.signOut()
                                onSignedOut()
                            }
                        },
                        label = { Text("Reset token") },
                        secondaryLabel = { Text("Fetch catalog token again") },
                        colors = tidalSecondaryChipColors(),
                    )
                }
                item { DisabledSettingChip("Download quality", "Coming in pass-4") }
                item { DisabledSettingChip("Download over LTE", "Coming in pass-4") }
                item { DisabledSettingChip("Storage limit", "Coming in pass-4") }
                item { DisabledSettingChip("Headphones required", "Coming in pass-4") }
                item { DisabledSettingChip("Sign in to TIDAL", "Coming soon") }
                item {
                    Text(
                        text = "Stream policy: AAC/MP4A non-lossless to preserve battery.",
                        style = MaterialTheme.typography.caption1,
                        color = TidalColors.OnSurfaceDim,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
    }
}

@Composable
private fun DisabledSettingChip(label: String, secondary: String) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        onClick = { },
        enabled = false,
        label = { Text(label) },
        secondaryLabel = { Text(secondary) },
        colors = tidalSecondaryChipColors(),
    )
}

@Composable
private fun PresetChip(label: String, value: AudioPreset, selected: AudioPreset, onClick: () -> Unit) {
    ToggleChip(
        checked = value == selected,
        onCheckedChange = { onClick() },
        label = { Text(label) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.radioIcon(checked = value == selected),
                contentDescription = null,
                tint = if (value == selected) TidalColors.Cyan else TidalColors.OnSurfaceMuted,
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = TidalColors.Surface,
            checkedEndBackgroundColor = TidalColors.Surface,
            checkedContentColor = TidalColors.White,
            uncheckedStartBackgroundColor = Color.Transparent,
            uncheckedContentColor = TidalColors.OnSurfaceDim,
        ),
    )
}

