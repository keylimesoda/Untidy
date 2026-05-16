package com.tidal.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun TidalChip(
    label: String,
    secondaryLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ChipColors = tidalSecondaryChipColors(),
    icon: (@Composable BoxScope.() -> Unit)? = { DefaultCircleIcon(label) },
) {
    Chip(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        onClick = onClick,
        colors = colors,
        icon = icon,
        label = { OneLineChipText(label) },
        secondaryLabel = secondaryLabel?.let { text -> { OneLineChipText(text) } },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Composable
fun NavChip(
    label: String,
    secondaryLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconText: String = label,
) {
    TidalChip(
        label = label,
        secondaryLabel = secondaryLabel,
        onClick = onClick,
        modifier = modifier,
        colors = tidalSecondaryChipColors(),
        icon = { DefaultCircleIcon(iconText) },
    )
}

@Composable
fun PlaceholderChip(modifier: Modifier = Modifier) {
    val placeholder = placeholderColor()
    Chip(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        onClick = {},
        enabled = false,
        colors = tidalSecondaryChipColors(),
        icon = { SkeletonCircle(placeholder) },
        label = { SkeletonLine(widthFraction = 0.70f, height = 14.dp, color = placeholder) },
        secondaryLabel = { SkeletonLine(widthFraction = 0.46f, height = 11.dp, color = placeholder) },
    )
}

@Composable
fun DefaultCircleIcon(text: String) {
    val initial = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
    Box(
        modifier = Modifier
            .size(ChipDefaults.LargeIconSize)
            .clip(RoundedCornerShape(4.dp))
            .background(TidalColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, fontWeight = FontWeight.Black, color = TidalColors.White)
    }
}

@Composable
private fun OneLineChipText(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SkeletonCircle(color: Color) {
    Box(
        Modifier
            .size(ChipDefaults.LargeIconSize)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(color),
    )
}

@Composable
private fun placeholderColor(): Color = TidalColors.SurfaceHigh

@Composable
fun tidalSecondaryChipColors(): ChipColors = ChipDefaults.secondaryChipColors(
    backgroundColor = TidalColors.Surface,
    contentColor = TidalColors.White,
    secondaryContentColor = TidalColors.OnSurfaceDim,
    iconColor = TidalColors.White,
)
