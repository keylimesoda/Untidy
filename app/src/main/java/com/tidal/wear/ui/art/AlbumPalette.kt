package com.tidal.wear.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

data class AlbumPalette(
    val lightVibrant: Color? = null,
    val vibrant: Color? = null,
    val dominant: Color? = null,
    val muted: Color? = null,
    val darkVibrant: Color? = null,
    val darkMuted: Color? = null,
) {
    fun accentColor(): Color {
        val color = vibrant ?: lightVibrant ?: darkVibrant ?: dominant ?: muted ?: Color.White
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.42f)
        hsl[2] = hsl[2].coerceIn(0.42f, 0.72f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    companion object {
        val Default = AlbumPalette()
    }
}
