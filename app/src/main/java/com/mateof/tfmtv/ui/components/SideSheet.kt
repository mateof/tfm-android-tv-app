package com.mateof.tfmtv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.palette

/**
 * A panel from the right edge. A dialog, so the D-pad cannot leave it.
 *
 * It often opens while OK is still held (a long press): the end of that press
 * belongs to the card it started on, so nothing of the centre key is let
 * through until it starts here.
 */
@Composable
fun SideSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val visible = remember { MutableTransitionState(false) }.apply { targetState = true }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .onPreviewKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (!ok || armed) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        armed = true
                        false
                    } else true
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(140)) + slideInHorizontally(tween(220)) { it / 3 }
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF18213A), Color(0xFF0B101C))),
                            RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                        )
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(start = 24.dp, end = 28.dp, top = 30.dp)
                        .focusGroup()
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun SheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    detail: String? = null,
    checked: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = palette.accent,
            focusedContentColor = palette.onAccent,
            pressedContainerColor = palette.accentEnd,
            pressedContentColor = palette.onAccent
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium)
            if (checked) Icon(Icons.Filled.Check, contentDescription = "Seleccionado", modifier = Modifier.size(18.dp))
        }
    }
}

/** One entry of an [ActionMenu]. */
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** What can be done with whatever was long-pressed. It closes before the action runs. */
@Composable
fun ActionMenu(title: String, actions: List<MenuAction>, onDismiss: () -> Unit, subtitle: String? = null) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestSoon() }
    SideSheet(title, onDismiss, subtitle) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            itemsIndexed(actions) { index, action ->
                SheetRow(
                    label = action.label,
                    icon = action.icon,
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier
                )
            }
        }
    }
}
