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
    val context = LocalContext.current
    var bitmap by remember(track?.artworkUrl) { mutableStateOf<Bitmap?>(null) }
    var art by remember(track?.artworkUrl) { mutableStateOf<AlbumArt?>(AlbumArt(null, AlbumPalette.Default)) }

    LaunchedEffect(track?.artworkUrl) {
        val url = track?.artworkUrl
        if (url.isNullOrBlank()) {
            bitmap = null
            art = AlbumArt(null, AlbumPalette.Default)
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(Size.ORIGINAL)
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

    DisposableEffect(track?.artworkUrl) {
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
        dominant = palette.dominantSwatch?.rgb?.let(::Color),
        muted = palette.mutedSwatch?.rgb?.let(::Color),
        darkVibrant = palette.darkVibrantSwatch?.rgb?.let(::Color),
        darkMuted = palette.darkMutedSwatch?.rgb?.let(::Color),
    )
}
