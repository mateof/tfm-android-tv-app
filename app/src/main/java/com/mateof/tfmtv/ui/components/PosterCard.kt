package com.mateof.tfmtv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 2:3 poster with the watch state on it. [width] null fills the cell of a grid. */
@Composable
fun PosterTile(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    completed: Boolean = false,
    rating: Double? = null,
    placeholder: ImageVector = Icons.Outlined.Movie,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        FocusCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
        ) {
            Art(imageUrl, title, placeholder)
            if (rating != null && rating > 0 && focused) {
                Badge("★ %.1f".format(rating), Modifier.align(Alignment.TopStart).padding(7.dp))
            }
            WatchMarks(progress, completed)
        }
        CardCaption(title, subtitle, focused)
    }
}
