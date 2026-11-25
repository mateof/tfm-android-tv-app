package com.mateof.tfmtv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.ui.theme.palette

private val Resting = Color.White.copy(alpha = 0.09f)

@Composable
internal fun pillColors(selected: Boolean = false) = ClickableSurfaceDefaults.colors(
    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Resting,
    contentColor = if (selected) palette.accent else MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = palette.accent,
    focusedContentColor = palette.onAccent,
    pressedContainerColor = palette.accentEnd,
    pressedContentColor = palette.onAccent,
    disabledContainerColor = Color.White.copy(alpha = 0.04f),
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
)

@Composable
fun PillButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The action the screen is for: it stands out before it has the focus. */
    primary: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (enabled) Modifier.tapClick(onClick) else Modifier),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = if (primary) {
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.92f),
                contentColor = Color(0xFF0B0F17),
                focusedContainerColor = palette.accent,
                focusedContentColor = palette.onAccent,
                pressedContainerColor = palette.accentEnd,
                pressedContentColor = palette.onAccent,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else pillColors()
    ) {
        Row(
            Modifier.height(38.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** An icon that says what it does once it has the focus, so a row of them stays short. */
@Composable
fun IconPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    expand: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.tapClick(onClick) else Modifier),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = pillColors(selected)
    ) {
        Row(
            Modifier.height(38.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            AnimatedVisibility(
                visible = focused && expand,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
    }
}
