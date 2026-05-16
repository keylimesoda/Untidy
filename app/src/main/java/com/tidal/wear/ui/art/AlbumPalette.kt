package com.tidal.wear.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

data class AlbumPalette(
    val lightVibrant: Color? = null,
    val dominant: Color? = null,
    val muted: Color? = null,
    val darkVibrant: Color? = null,
    val darkMuted: Color? = null,
) {
    fun accentColor(): Color {
        val color = lightVibrant ?: dominant ?: muted ?: Color.White
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[2] = hsl[2].coerceAtLeast(0.5f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    companion object {
        val Default = AlbumPalette()
    }
}
