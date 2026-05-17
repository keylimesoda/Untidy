package com.tidal.wear.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import coil.size.Size
import com.tidal.wear.ui.art.rememberArtworkPalette
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun PlaylistArtwork(
    artworkUrl: String?,
    trackArtworkUrls: List<String>,
    accent: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    artworkBitmap: ImageBitmap? = null,
    size: Dp = 92.dp,
    cornerRadius: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val panelUrls = trackArtworkUrls
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(4)
        .toList()

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(lerp(TidalColors.SurfaceHigh, accent, 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            val loadedBitmap = artworkBitmap ?: rememberArtworkPalette(artworkUrl, Size(160, 160)).bitmap
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlaylistFallbackGlyph()
            }
        } else if (panelUrls.isNotEmpty()) {
            PlaylistArtworkGrid(panelUrls = panelUrls, accent = accent)
        } else {
            PlaylistFallbackGlyph()
        }
    }
}

@Composable
private fun PlaylistArtworkGrid(
    panelUrls: List<String>,
    accent: Color,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            PlaylistArtworkPanel(panelUrls.getOrNull(0), accent, Modifier.weight(1f))
            PlaylistArtworkPanel(panelUrls.getOrNull(1), accent, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f)) {
            PlaylistArtworkPanel(panelUrls.getOrNull(2), accent, Modifier.weight(1f))
            PlaylistArtworkPanel(panelUrls.getOrNull(3), accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlaylistArtworkPanel(
    artworkUrl: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(lerp(TidalColors.SurfaceHigh, accent, 0.24f)),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlaylistFallbackGlyph() {
    Text("≡", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
}
