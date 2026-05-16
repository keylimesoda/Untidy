package com.tidal.wear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun LinearTrackProgress(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
    val trackColor = TidalColors.TrackDim
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(10.dp),
        ) {
            val y = size.height / 2f
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                color = trackColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = stroke.width,
                cap = stroke.cap,
            )
            drawLine(
                color = accent,
                start = Offset(0f, y),
                end = Offset(size.width * progress.coerceIn(0f, 1f), y),
                strokeWidth = stroke.width,
                cap = stroke.cap,
            )
        }
        Text(
            text = "${positionMs.asClock()} / ${durationMs.asClock()}",
            style = MaterialTheme.typography.caption2,
            fontSize = 12.sp,
            color = TidalColors.OnSurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private fun Long.asClock(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
