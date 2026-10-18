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
fun Modifier.tapClick(onClick: () -> Unit): Modifier = tapClick(onClick, null)

/**
 * Same, with a long press. A separate overload on purpose: with a defaulted
 * second lambda, `tapClick { … }` would bind the trailing lambda to it.
 */
@Composable
fun Modifier.tapClick(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier {
    val current by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    return pointerInput(onLongClick != null) {
        detectTapGestures(
            onLongPress = if (long != null) ({ long?.invoke() }) else null,
            onTap = { current() }
        )
    }
}
