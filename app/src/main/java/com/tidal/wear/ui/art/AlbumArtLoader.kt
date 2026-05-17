package com.tidal.wear.ui.art

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AlbumArt(
    val bitmap: ImageBitmap?,
    val palette: AlbumPalette,
    val byteCount: Int = 0,
)

private val albumArtCache = object : LruCache<String, AlbumArt>(4 * 1024) {
    override fun sizeOf(key: String, value: AlbumArt): Int =
        (value.byteCount / 1024).coerceAtLeast(1)
}

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
    val appContext = context.applicationContext
    val cacheKey = remember(artworkUrl, requestSize) { artworkCacheKey(artworkUrl, requestSize) }
    var art by remember(cacheKey) {
        mutableStateOf(cacheKey?.let(::cachedAlbumArt) ?: AlbumArt(null, AlbumPalette.Default))
    }

    LaunchedEffect(cacheKey) {
        val url = artworkUrl?.trim()
        val key = cacheKey
        if (url.isNullOrBlank() || key == null) {
            art = AlbumArt(null, AlbumPalette.Default)
            return@LaunchedEffect
        }
        cachedAlbumArt(key)?.let {
            art = it
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .allowHardware(false)
                .size(requestSize)
                .memoryCacheKey(key)
                .diskCacheKey(url)
                .build()
            (appContext.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        }
        art = loaded?.let {
            AlbumArt(
                bitmap = it.asImageBitmap(),
                palette = it.toAlbumPalette(),
                byteCount = it.allocationByteCount,
            )
        } ?: AlbumArt(null, AlbumPalette.Default)
        putAlbumArt(key, art)
    }
    return art
}

private fun artworkCacheKey(artworkUrl: String?, requestSize: Size): String? =
    artworkUrl?.trim()?.takeIf(String::isNotBlank)?.let { "$it|$requestSize" }

private fun cachedAlbumArt(key: String): AlbumArt? = synchronized(albumArtCache) { albumArtCache.get(key) }

private fun putAlbumArt(key: String, art: AlbumArt) {
    if (art.bitmap != null) {
        synchronized(albumArtCache) { albumArtCache.put(key, art) }
    }
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
