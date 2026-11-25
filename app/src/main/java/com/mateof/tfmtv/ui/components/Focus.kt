package com.mateof.tfmtv.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

/** Where the focus goes when the user enters a section from the rail. */
val LocalEntryFocus = staticCompositionLocalOf { FocusRequester() }

@Composable
fun Modifier.entryFocus(enabled: Boolean = true): Modifier =
    if (enabled) focusRequester(LocalEntryFocus.current) else this

/**
 * Gives the focus back to the card a screen was left through: it keeps the
 * requester attached to that card and fires once, after it has been laid out.
 */
class FocusReturn(val key: String?) {
    val requester = FocusRequester()
    var done = false
}

@Composable
fun rememberFocusReturn(key: String?, vararg scope: Any?): FocusReturn =
    // Deliberately not rememberSaveable: navigating away and back must restore
    // the focus again, and a saved "already done" flag would swallow it.
    remember(key, *scope) { FocusReturn(key) }

@Composable
fun Modifier.returnFocus(focus: FocusReturn, itemKey: String): Modifier {
    if (focus.key == null || itemKey != focus.key) return this
    if (!focus.done) {
        LaunchedEffect(Unit) {
            delay(80)
            focus.requester.requestSoon()
            focus.done = true
        }
    }
    return focusRequester(focus.requester)
}

/** Keeps the focused row of a screen at the same height, right below [top]. */
private class TopPivot(private val top: Float) : BringIntoViewSpec {
    @Deprecated("Not used by the list any more; the interface still has it")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(260)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - top
}

/** [content] is the list of rows, and gets the spec its own rows have to scroll with. */
@Composable
fun PivotRows(top: Dp, content: @Composable (rows: BringIntoViewSpec) -> Unit) {
    val rows = LocalBringIntoViewSpec.current
    val px = with(LocalDensity.current) { top.toPx() }
    val pivot = remember(px) { TopPivot(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pivot) { content(rows) }
}

/** A titled row of cards that remembers which one had the focus. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    spec: BringIntoViewSpec? = null,
    spacing: Dp = 14.dp,
    content: LazyListScope.() -> Unit
) {
    Column(modifier) {
        Row(
            Modifier.padding(start = ContentStart, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted) }
        }
        val state = rememberLazyListState()
        val row: @Composable () -> Unit = {
            LazyRow(
                state = state,
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(start = ContentStart, end = ContentEnd, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                content = content
            )
        }
        if (spec == null) row() else CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = row)
    }
}
