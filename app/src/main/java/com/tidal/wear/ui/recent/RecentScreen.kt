package com.tidal.wear.ui.recent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.tidal.wear.recent.RecentItem
import com.tidal.wear.recent.RecentItemType
import com.tidal.wear.ui.components.TidalResultChip
import com.tidal.wear.ui.components.WearListPadding
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun RecentScreen(
    items: List<RecentItem>,
    onOpenTrack: (RecentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = WearListPadding.RoundScreenCompact,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { RecentTitle() }
            if (items.isEmpty()) {
                item { EmptyRecentChip() }
            } else {
                items.forEach { item ->
                    item {
                        TidalResultChip(
                            label = item.title,
                            secondaryLabel = item.secondaryLabel(),
                            artworkUrl = item.artworkUrl,
                            fallback = item.type.fallback,
                            onClick = {
                                when (item.type) {
                                    RecentItemType.Track -> onOpenTrack(item)
                                    RecentItemType.Album -> onOpenAlbum(item.id)
                                    RecentItemType.Playlist -> onOpenPlaylist(item.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTitle() {
    Text(
        text = "Recent",
        color = TidalColors.White,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyRecentChip() {
    Chip(
        onClick = {},
        enabled = false,
        label = { Text("Nothing recent yet") },
        secondaryLabel = { Text("Play music to build this list") },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = TidalColors.Surface,
            contentColor = TidalColors.White,
            secondaryContentColor = TidalColors.OnSurfaceMuted,
        ),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

private fun RecentItem.secondaryLabel(): String = when (type) {
    RecentItemType.Track -> subtitle.ifBlank { "Track" }
    RecentItemType.Album -> listOf("Album", subtitle).filter { it.isNotBlank() && it != "Album" }.joinToString(" · ").ifBlank { "Album" }
    RecentItemType.Playlist -> listOf("Playlist", subtitle).filter { it.isNotBlank() && it != "Playlist" }.joinToString(" · ").ifBlank { "Playlist" }
}

private val RecentItemType.fallback: String
    get() = when (this) {
        RecentItemType.Track -> "♪"
        RecentItemType.Album -> "▣"
        RecentItemType.Playlist -> "≡"
    }
