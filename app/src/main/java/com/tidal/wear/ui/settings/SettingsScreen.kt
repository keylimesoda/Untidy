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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tidal.wear.BuildConfig
import com.tidal.wear.core.auth.AuthState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.ReleaseVersionPreference
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
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
    val releaseVersionPreference by repository.releaseVersionPreference.collectAsState(initial = ReleaseVersionPreference.Explicit)
    val authState by authRepository.authState.collectAsState(initial = AuthState.Initializing)
    val accountInfo by authRepository.accountInfo.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmErase by remember { mutableStateOf(false) }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
        ) {
                item { SectionHeader("Account") }
                if (accountInfo != null) {
                    item {
                        StatusChip(
                            label = "TIDAL account",
                            secondary = "User #${accountInfo?.userId.orEmpty()}",
                            active = true,
                        )
                    }
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            onClick = { confirmErase = true },
                            label = { Text("Erase account") },
                            secondaryLabel = { Text("Sign out on this watch") },
                            colors = tidalSecondaryChipColors(),
                        )
                    }
                    if (confirmErase) {
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                onClick = { confirmErase = false },
                                label = { Text("Cancel") },
                                colors = tidalSecondaryChipColors(),
                            )
                        }
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                onClick = {
                                    confirmErase = false
                                    scope.launch {
                                        authRepository.signOut()
                                        onSignedOut()
                                    }
                                },
                                label = { Text("Confirm erase") },
                                secondaryLabel = { Text("Remove account from watch") },
                                colors = ChipDefaults.primaryChipColors(backgroundColor = TidalColors.Cyan, contentColor = TidalColors.Black),
                            )
                        }
                    }
                } else {
                    item {
                        StatusChip(
                            label = "No linked account",
                            secondary = "Linking starts from app open",
                            active = false,
                        )
                    }
                }

                item { SectionHeader("Playback") }
                item {
                    Text(
                        text = "Streaming quality",
                        style = MaterialTheme.typography.caption1,
                        color = TidalColors.OnSurfaceDim,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        textAlign = TextAlign.Center,
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

                item { SectionHeader("Catalog") }
                item {
                    Text(
                        text = "Duplicate releases",
                        style = MaterialTheme.typography.caption1,
                        color = TidalColors.OnSurfaceDim,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    ReleaseVersionChip("Prefer explicit", ReleaseVersionPreference.Explicit, releaseVersionPreference) {
                        scope.launch { repository.setReleaseVersionPreference(ReleaseVersionPreference.Explicit) }
                    }
                }
                item {
                    ReleaseVersionChip("Prefer clean", ReleaseVersionPreference.Clean, releaseVersionPreference) {
                        scope.launch { repository.setReleaseVersionPreference(ReleaseVersionPreference.Clean) }
                    }
                }

                item { SectionHeader("Downloads") }
                item { DisabledSettingChip("Offline playback", "Needs sanctioned TIDAL support") }
                item { DisabledSettingChip("Download quality", "Disabled until supported") }
                item { DisabledSettingChip("Download over LTE", "Disabled until supported") }
                item { DisabledSettingChip("Storage limit", "Disabled until supported") }

                item { SectionHeader("About") }
                item { DisabledSettingChip("Untidy", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }

                if (BuildConfig.DEBUG) {
                    item { SectionHeader("Debug") }
                    item { DisabledSettingChip("Auth state", authState.label()) }
                    item { DisabledSettingChip("Token client", accountInfo?.tokenClientId ?: "No linked account") }
                    item { DisabledSettingChip("Scopes", accountInfo?.scopes?.sorted()?.joinToString(" ").orEmpty().ifBlank { "None" }) }
                    item { DisabledSettingChip("Playback backend", "Direct manifest BTS") }
                }

                item { SectionHeader("Legal") }
                item { DisabledSettingChip("Open-source licenses", "Coming before release") }
                item { DisabledSettingChip("Privacy", "Tokens are local and erasable") }
                item { DisabledSettingChip("TIDAL attribution", "Coming before release") }
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
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        color = TidalColors.Cyan,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.caption1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusChip(label: String, secondary: String, active: Boolean) {
    Chip(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        onClick = { },
        icon = {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (active) TidalColors.Cyan else TidalColors.OnSurfaceMuted),
            )
        },
        label = { Text(label) },
        secondaryLabel = { Text(secondary) },
        colors = tidalSecondaryChipColors(),
    )
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

@Composable
private fun ReleaseVersionChip(
    label: String,
    value: ReleaseVersionPreference,
    selected: ReleaseVersionPreference,
    onClick: () -> Unit,
) {
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

private fun AuthState.label(): String = when (this) {
    AuthState.Anonymous -> "Anonymous"
    AuthState.Initializing -> "Initializing"
    AuthState.UserSignedIn -> "User signed in"
}

