package com.tidal.wear.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.tidal.wear.core.playback.offline.offlineDownloadsStorageBytes
import com.tidal.wear.core.playback.offline.readOfflineDownloadedTracks
import com.tidal.wear.core.playback.offline.removeAllOfflineTrackDownloads
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.components.tidalSecondaryChipColors
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    authRepository: TidalAuthRepository,
    onOpenDownloads: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository(context.applicationContext) }
    val preset by repository.preset.collectAsState(initial = AudioPreset.BatterySaver)
    val releaseVersionPreference by repository.releaseVersionPreference.collectAsState(initial = ReleaseVersionPreference.Explicit)
    val downloadOverLte by repository.downloadOverLte.collectAsState(initial = false)
    val authState by authRepository.authState.collectAsState(initial = AuthState.Initializing)
    val accountInfo by authRepository.accountInfo.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmErase by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }
    var downloadsRefreshTick by remember { mutableIntStateOf(0) }
    val downloadedTracks = remember(downloadsRefreshTick) { context.readOfflineDownloadedTracks() }
    val downloadsStorageBytes = remember(downloadsRefreshTick) { context.offlineDownloadsStorageBytes() }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = WearListPadding.RoundScreen,
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
                            label = { Text("Sign out") },
                            secondaryLabel = { Text(accountSignOutSecondary(downloadedTracks.size)) },
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
                                label = { Text("Confirm sign out") },
                                secondaryLabel = { Text(accountSignOutConfirmSecondary(downloadedTracks.size)) },
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
                    PresetChip("Battery Saver", "Lowest data and power", AudioPreset.BatterySaver, preset) {
                        scope.launch { repository.setPreset(AudioPreset.BatterySaver) }
                    }
                }
                item {
                    PresetChip("Balanced", "Efficient everyday listening", AudioPreset.Balanced, preset) {
                        scope.launch { repository.setPreset(AudioPreset.Balanced) }
                    }
                }
                item {
                    PresetChip("High", "Up to 320 kbps", AudioPreset.High, preset) {
                        scope.launch { repository.setPreset(AudioPreset.High) }
                    }
                }
                item {
                    PresetChip("Full fidelity", "Lossless / HiRes source", AudioPreset.FullFidelity, preset) {
                        scope.launch { repository.setPreset(AudioPreset.FullFidelity) }
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
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        onClick = onOpenDownloads,
                        label = { Text("Manage downloads") },
                        secondaryLabel = { Text(downloadsSummary(downloadedTracks.size, downloadsStorageBytes)) },
                        colors = tidalSecondaryChipColors(),
                    )
                }
                item { DisabledSettingChip("Download quality", "Fixed for this release") }
                item {
                    BooleanSettingChip(
                        label = "Download over LTE",
                        secondary = if (downloadOverLte) "Uses watch data plan" else "Wi‑Fi only",
                        checked = downloadOverLte,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setDownloadOverLte(enabled) }
                        },
                    )
                }
                item { DisabledSettingChip("Storage limit", "Manual cleanup for now") }
                if (downloadedTracks.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            onClick = { confirmRemoveDownloads = true },
                            label = { Text("Remove all downloads") },
                            secondaryLabel = { Text("Local files only") },
                            colors = tidalSecondaryChipColors(),
                        )
                    }
                    if (confirmRemoveDownloads) {
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                onClick = { confirmRemoveDownloads = false },
                                label = { Text("Cancel") },
                                secondaryLabel = { Text("Keeps local downloads") },
                                colors = tidalSecondaryChipColors(),
                            )
                        }
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                onClick = {
                                    if (context.removeAllOfflineTrackDownloads()) {
                                        confirmRemoveDownloads = false
                                        downloadsRefreshTick++
                                    }
                                },
                                label = { Text("Remove local copies?") },
                                secondaryLabel = { Text("Keeps TIDAL library") },
                                colors = ChipDefaults.primaryChipColors(backgroundColor = TidalColors.Cyan, contentColor = TidalColors.Black),
                            )
                        }
                    }
                }

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
                item { DisabledSettingChip("Third-party licenses", "Kotlin · Compose · Media3 · TIDAL SDK") }
                item { DisabledSettingChip("Privacy", "Tokens are local and erasable") }
                item { DisabledSettingChip("TIDAL attribution", "Unofficial client · TIDAL marks belong to TIDAL") }
                item {
                    Text(
                        text = streamingPolicyFooter(preset),
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

// Informational status row. Deliberately not a Chip: an inert control that
// presents a chip's tap affordance reads as a dead button (UX-002).
@Composable
private fun StatusChip(label: String, secondary: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TidalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) TidalColors.Cyan else TidalColors.OnSurfaceMuted),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TidalColors.White)
            Text(text = secondary, color = TidalColors.OnSurfaceDim)
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
private fun PresetChip(
    label: String,
    secondary: String,
    value: AudioPreset,
    selected: AudioPreset,
    onClick: () -> Unit,
) {
    ToggleChip(
        checked = value == selected,
        onCheckedChange = { onClick() },
        label = { Text(label) },
        secondaryLabel = { Text(secondary) },
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
private fun BooleanSettingChip(
    label: String,
    secondary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(label) },
        secondaryLabel = { Text(secondary) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked),
                contentDescription = null,
                tint = if (checked) TidalColors.Cyan else TidalColors.OnSurfaceMuted,
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

internal fun streamingPolicyFooter(preset: AudioPreset): String = when (preset) {
    AudioPreset.BatterySaver -> "Battery Saver uses efficient AAC to protect battery."
    AudioPreset.Balanced -> "Balanced uses efficient AAC for everyday listening."
    AudioPreset.High -> "High requests up to 320 kbps AAC where available."
    AudioPreset.FullFidelity -> "Full fidelity requests lossless / HiRes where available. Uses more data and battery."
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

private fun downloadsSummary(count: Int, bytes: Long): String {
    val countText = when (count) {
        0 -> "No downloads"
        1 -> "1 track"
        else -> "$count tracks"
    }
    val sizeText = formatStorageBytes(bytes)
    return if (bytes > 0L) "$countText · $sizeText" else countText
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "0 MB"
}

// Wording must state the local-only effect: the action signs out and never
// deletes the TIDAL account (UX-003; "erase" implied account destruction).
internal fun accountSignOutSecondary(downloadCount: Int): String =
    if (downloadCount > 0) "Removes account from this watch · keeps downloads" else "Removes account from this watch"

internal fun accountSignOutConfirmSecondary(downloadCount: Int): String =
    if (downloadCount > 0) "Downloads stay on this watch" else "Nothing is deleted from TIDAL"

