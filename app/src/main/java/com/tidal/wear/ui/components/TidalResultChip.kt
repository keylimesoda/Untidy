package com.tidal.wear.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import coil.size.Size
import com.tidal.wear.ui.art.rememberDeferredRowArtwork
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun TidalResultChip(
    label: String,
    secondaryLabel: String,
    artworkUrl: String?,
    fallback: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableArtwork: Boolean = true,
    deferArtwork: Boolean = true,
) {
    val artwork = rememberDeferredRowArtwork(
        artworkUrl = artworkUrl,
        requestSize = Size(96, 96),
        enabled = enableArtwork,
        delayMillis = if (deferArtwork) 160L else 0L,
    )
    val accent = TidalColors.Cyan
    Chip(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = lerp(TidalColors.Surface, accent, 0.28f),
            contentColor = TidalColors.White,
            secondaryContentColor = lerp(TidalColors.OnSurfaceDim, Color.White, 0.28f),
            iconColor = TidalColors.White,
        ),
        icon = {
            RowArtworkThumb(
                label = label,
                fallback = fallback,
                bitmap = artwork,
                accent = accent,
            )
        },
        label = { ResultText(label) },
        secondaryLabel = { ResultText(secondaryLabel) },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Composable
fun RowArtworkThumb(
    label: String,
    fallback: String,
    bitmap: ImageBitmap?,
    accent: Color = TidalColors.Cyan,
) {
    Box(
        modifier = Modifier
            .size(ChipDefaults.LargeIconSize)
            .clip(RoundedCornerShape(6.dp))
            .background(lerp(TidalColors.SurfaceHigh, accent, 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: fallback,
                color = TidalColors.White,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ResultText(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
