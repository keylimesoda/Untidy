package com.tidal.wear.ui.art

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AlbumArt(
    val bitmap: ImageBitmap?,
    val palette: AlbumPalette,
)

@Composable
fun rememberAlbumArt(track: TidalTrack?): AlbumArt? {
    return rememberArtworkPalette(track?.artworkUrl, Size.ORIGINAL)
}

@Composable
fun rememberArtworkPalette(
    artworkUrl: String?,
    requestSize: Size = Size(160, 160),
): AlbumArt {
    val context = LocalContext.current
    var bitmap by remember(artworkUrl, requestSize) { mutableStateOf<Bitmap?>(null) }
    var art by remember(artworkUrl, requestSize) { mutableStateOf(AlbumArt(null, AlbumPalette.Default)) }

    LaunchedEffect(artworkUrl, requestSize) {
        val url = artworkUrl
        if (url.isNullOrBlank()) {
            bitmap = null
            art = AlbumArt(null, AlbumPalette.Default)
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(requestSize)
                .build()
            (ImageLoader(context).execute(request).drawable as? BitmapDrawable)?.bitmap
        }
        bitmap = loaded
        art = loaded?.let {
            AlbumArt(
                bitmap = it.asImageBitmap(),
                palette = it.toAlbumPalette(),
            )
        } ?: AlbumArt(null, AlbumPalette.Default)
    }

    DisposableEffect(artworkUrl, requestSize) {
        onDispose {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
            bitmap = null
        }
    }
    return art
}

private fun Bitmap.toAlbumPalette(): AlbumPalette {
    val palette = Palette.from(this).generate()
    return AlbumPalette(
        lightVibrant = palette.lightVibrantSwatch?.rgb?.let(::Color),
        vibrant = palette.vibrantSwatch?.rgb?.let(::Color),
        dominant = palette.dominantSwatch?.rgb?.let(::Color),
        muted = palette.mutedSwatch?.rgb?.let(::Color),
        darkVibrant = palette.darkVibrantSwatch?.rgb?.let(::Color),
        darkMuted = palette.darkMutedSwatch?.rgb?.let(::Color),
    )
}
