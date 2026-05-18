package com.tidal.wear.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable

@Composable
fun Modifier.rotaryScrollableWithFocus(scrollableState: ScrollableState): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    return focusRequester(focusRequester)
        .focusable()
        .rotaryScrollable(
            behavior = RotaryScrollableDefaults.behavior(scrollableState = scrollableState),
            focusRequester = focusRequester,
        )
}
