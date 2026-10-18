package com.mateof.tfmtv.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay

/**
 * A text field that only takes focus (and opens the keyboard) when clicked.
 *
 * Moving the D-pad over a bare text field focuses it, and on TV that pops the
 * keyboard up every time. Here the D-pad lands on a focusable shell instead;
 * OK or a tap hands focus to the real field, and leaving it hands it back.
 * [field] receives the modifier for the field and a callback for its IME action.
 */
@Composable
fun ClickToEdit(
    modifier: Modifier = Modifier,
    field: @Composable (fieldModifier: Modifier, done: () -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var shellFocused by remember { mutableStateOf(false) }
    var fieldWasFocused by remember { mutableStateOf(false) }
    val shell = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        // The field becomes focusable in the same frame, so it may not be attached yet.
        repeat(5) {
            if (runCatching { inner.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    val done = {
        editing = false
        runCatching { shell.requestFocus() }
        Unit
    }

    Box(
        modifier
            .focusRequester(shell)
            .onFocusChanged { shellFocused = it.isFocused }
            .onKeyEvent { event ->
                if (editing) return@onKeyEvent false
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isOk) return@onKeyEvent false
                if (event.type == KeyEventType.KeyUp) editing = true
                true
            }
            .focusable()
    ) {
        CompositionLocalProvider(LocalShellFocused provides (shellFocused && !editing)) {
            field(
                Modifier
                    .focusRequester(inner)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged {
                        if (it.isFocused) fieldWasFocused = true
                        else if (fieldWasFocused) {
                            fieldWasFocused = false
                            editing = false
                        }
                    }
                    .dpadEscape(focusManager)
                    // The first BACK only hides the keyboard (the IME takes it);
                    // one that reaches the field means "done editing". Without
                    // it, left/right keep moving the cursor and the buttons
                    // beside the field are out of reach.
                    .onPreviewKeyEvent { event ->
                        if (event.key != Key.Back) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) done()
                        true
                    },
                done
            )
        }
        if (!editing) Box(Modifier.matchParentSize().tapClick { editing = true })
    }
}

private val LocalShellFocused = compositionLocalOf { false }

/**
 * Colours for an outlined field inside [ClickToEdit]: it looks focused while
 * the D-pad rests on it, though the keyboard only opens on OK.
 */
@Composable
fun clickToEditColors(): TextFieldColors {
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    return if (LocalShellFocused.current) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = primary,
            unfocusedLabelColor = primary,
            unfocusedLeadingIconColor = primary
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }
}
