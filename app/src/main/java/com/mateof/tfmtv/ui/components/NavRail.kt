package com.mateof.tfmtv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.R
import com.mateof.tfmtv.ui.theme.Background
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.palette

/** Width the content leaves free for the rail, which widens over it. */
val RailWidth = 64.dp

/**
 * Icons that widen into labels while the rail holds the focus. It floats over
 * the content instead of pushing it: resizing a grid of cards on every move
 * was slow and threw the cards around.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavRail(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** The current section: entering the rail always lands on it. */
    selected: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val width by animateDpAsState(if (expanded) 220.dp else RailWidth, tween(220), label = "railWidth")
    val veil by animateFloatAsState(if (expanded) 1f else 0f, tween(260), label = "railVeil")
    Box(modifier.fillMaxHeight()) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(520.dp)
                .drawBehind {
                    if (veil > 0f) drawRect(
                        Brush.horizontalGradient(
                            0f to Background.copy(alpha = 0.97f * veil),
                            0.45f to Background.copy(alpha = 0.88f * veil),
                            1f to Color.Transparent
                        )
                    )
                }
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .onFocusChanged { onExpandedChange(it.hasFocus) }
                .focusProperties { enter = { selected } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 18.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                Modifier.height(48.dp).padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Logo(32)
                AnimatedVisibility(expanded, enter = fadeIn(tween(160, 80)), exit = fadeOut(tween(80))) {
                    Text("TFM TV", style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                }
            }
            content()
        }
    }
}

@Composable
fun Logo(sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 3.2f).dp))
            .background(palette.gradient),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_plane),
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size((sizeDp * 0.62f).dp)
        )
    }
}

@Composable
fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = palette.accent
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .tapClick(onClick),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected && expanded) Color.White.copy(alpha = 0.08f) else Color.Transparent,
            contentColor = if (selected) accent else OnSurfaceMuted,
            focusedContainerColor = accent,
            focusedContentColor = palette.onAccent,
            pressedContainerColor = palette.accentEnd,
            pressedContentColor = palette.onAccent
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp))
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
            AnimatedVisibility(expanded, enter = fadeIn(tween(160, 60)), exit = fadeOut(tween(80))) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A gap with a line between groups of the rail. */
@Composable
fun RailDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}
