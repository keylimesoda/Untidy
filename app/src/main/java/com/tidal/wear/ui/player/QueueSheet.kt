package com.tidal.wear.ui.player

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.playback.PlaybackQueueSnapshot
import com.tidal.wear.ui.components.rotaryScrollableWithFocus
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun QueueSheet(
    queue: PlaybackQueueSnapshot,
    currentTrack: TidalTrack?,
    onJumpToIndex: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val initialCenter = remember(queue.items.size, queue.currentIndex) {
        when {
            queue.hasKnownQueue -> (queue.currentIndex + 1).coerceAtLeast(0)
            else -> 0
        }
    }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = initialCenter)

    LaunchedEffect(queue.items.size, queue.currentIndex) {
        if (queue.hasKnownQueue) listState.scrollToItem((queue.currentIndex + 1).coerceAtLeast(0))
    }

    Box(modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollableWithFocus(listState),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { QueueHeader(queue) }
            when {
                queue.hasKnownQueue -> queue.items.forEachIndexed { index, track ->
                    item {
                        QueueRow(
                            index = index,
                            track = track,
                            isCurrent = index == queue.currentIndex,
                            onClick = {
                                if (index != queue.currentIndex && track.id.isNotBlank()) onJumpToIndex(index)
                            },
                        )
                    }
                }
                currentTrack != null -> {
                    item { SingleTrackState(currentTrack) }
                }
                else -> item { EmptyQueueState() }
            }
        }
    }
}

@Composable
private fun QueueHeader(queue: PlaybackQueueSnapshot) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(width = 24.dp, height = 3.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x4DFFFFFF)),
        )
        Text(
            text = "Queue",
            color = TidalColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Text(
            text = if (queue.hasKnownQueue) "${queue.currentIndex + 1}/${queue.items.size}" else "Current playback",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun QueueRow(
    index: Int,
    track: TidalTrack,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val enabled = track.id.isNotBlank()
    val description = when {
        isCurrent -> "Now playing, ${track.title}, ${track.artist}"
        enabled -> "Play ${track.title} by ${track.artist}"
        else -> "${track.title}, unavailable"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) TidalColors.Cyan.copy(alpha = 0.20f) else TidalColors.Surface.copy(alpha = 0.74f))
            .clickable(enabled = enabled && !isCurrent) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onClick()
            }
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isCurrent) TidalColors.Cyan else TidalColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isCurrent) "Now" else "${index + 1}",
                color = if (isCurrent) TidalColors.Black else TidalColors.OnSurfaceMuted,
                fontSize = if (isCurrent) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { "Untitled" },
                color = if (enabled) TidalColors.White else TidalColors.OnSurfaceDim,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.ifBlank { if (enabled) "Track" else "Unavailable" },
                color = if (isCurrent) TidalColors.Cyan else TidalColors.OnSurfaceMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SingleTrackState(track: TidalTrack) {
    Column(
        modifier = Modifier.fillMaxWidth(0.88f).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QueueRow(index = 0, track = track, isCurrent = true, onClick = {})
        Text(
            text = "Single track · start an album or playlist for what’s next.",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun EmptyQueueState() {
    Column(
        modifier = Modifier.fillMaxWidth(0.82f).padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = TidalColors.OnSurfaceMuted, modifier = Modifier.size(28.dp))
        Text(
            text = "No queue yet",
            color = TidalColors.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Start an album, playlist, or track list to see what’s next.",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
