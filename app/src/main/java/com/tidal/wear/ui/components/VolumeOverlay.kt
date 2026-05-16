package com.tidal.wear.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun VolumeOverlay(
    visible: Boolean,
    volume: Int,
    maxVolume: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val inactiveColor = TidalColors.TrackDim
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .background(
                    color = TidalColors.SurfaceHigh,
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Text("VOL", color = TidalColors.White, style = MaterialTheme.typography.caption2)
            Canvas(
                modifier = Modifier
                    .width(92.dp)
                    .height(8.dp),
            ) {
                val y = size.height / 2f
                val fraction = volume.toFloat() / maxVolume.coerceAtLeast(1).toFloat()
                drawLine(
                    color = inactiveColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction.coerceIn(0f, 1f), y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
