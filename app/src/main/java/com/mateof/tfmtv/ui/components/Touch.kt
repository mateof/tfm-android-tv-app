package com.mateof.tfmtv.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * tv-material surfaces only wire their `onClick` to the D-pad centre key, so a
 * finger tap does nothing at all. This puts touch back, which keeps the app
 * usable on a phone or tablet.
 */
@Composable
fun Modifier.tapClick(onClick: () -> Unit): Modifier {
    val current by rememberUpdatedState(onClick)
    return pointerInput(Unit) { detectTapGestures { current() } }
}
