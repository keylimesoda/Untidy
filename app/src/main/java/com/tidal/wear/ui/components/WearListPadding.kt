package com.tidal.wear.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Shared vertical breathing room for Wear browse lists on round displays.
 *
 * ScalingLazyColumn still provides the watch-native edge scaling/fade behavior; this padding
 * keeps the first and last actionable rows from settling directly under the circular bezel.
 */
object WearListPadding {
    val RoundScreenTop = 18.dp
    val RoundScreenBottom = 32.dp
    val RoundScreenCompactTop = 12.dp
    val RoundScreenCompactBottom = 28.dp

    val RoundScreen = PaddingValues(top = RoundScreenTop, bottom = RoundScreenBottom)
    val RoundScreenCompact = PaddingValues(top = RoundScreenCompactTop, bottom = RoundScreenCompactBottom)
}
