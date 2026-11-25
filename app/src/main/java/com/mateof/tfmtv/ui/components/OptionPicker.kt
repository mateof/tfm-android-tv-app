package com.mateof.tfmtv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

data class PickerOption<T>(val value: T, val label: String, val detail: String? = null)

/** Single choice from a list. Opens with the focus on the current choice. */
@Composable
fun <T> OptionPicker(
    title: String,
    options: List<PickerOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val start = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestSoon() }
    SideSheet(title, onDismiss) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            itemsIndexed(options) { index, option ->
                SheetRow(
                    label = option.label,
                    detail = option.detail,
                    checked = option.value == selected,
                    onClick = {
                        onSelect(option.value)
                        onDismiss()
                    },
                    modifier = if (index == start) Modifier.focusRequester(focus) else Modifier
                )
            }
        }
    }
}
