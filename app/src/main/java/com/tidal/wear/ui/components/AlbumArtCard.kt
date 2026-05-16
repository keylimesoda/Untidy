package com.tidal.wear.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun AlbumArtCard(
    bitmap: ImageBitmap?,
    accent: Color,
    modifier: Modifier = Modifier,
    sizeDp: Int = 132,
    cornerRadius: Dp = 6.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(shape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "♪",
                color = TidalColors.Black,
                fontWeight = FontWeight.Black,
                fontSize = 2.em,
            )
        }
    }
}
