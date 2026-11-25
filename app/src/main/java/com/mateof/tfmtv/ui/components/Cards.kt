package com.mateof.tfmtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.Star
import com.mateof.tfmtv.ui.theme.SurfaceVariant
import com.mateof.tfmtv.ui.theme.palette
import com.mateof.tfmtv.ui.theme.seedBrush

/** What every card is made of: it grows, gets a border of the accent and a glow when focused. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    corner: Dp = 12.dp,
    focusedScale: Float = 1.06f,
    container: Color = SurfaceVariant,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val colors = palette
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.tapClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, colors.gradient), shape = shape),
            pressedBorder = Border(BorderStroke(2.5.dp, colors.gradient), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = if (colors.effects) Glow(colors.accent.copy(alpha = 0.55f), 14.dp) else Glow.None
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = container,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = container,
            pressedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        content = content
    )
}

/** Title and detail below a card. The title of the focused one runs if it does not fit. */
@Composable
fun CardCaption(title: String, subtitle: String?, focused: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900) else Modifier
        )
        // Kept when empty, so the cards of a row stay aligned.
        Text(
            text = subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) palette.accent else OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A picture over the gradient and the icon that stand in for it until it loads, or if it never does. */
@Composable
fun Art(
    url: String?,
    seed: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 34.dp
) {
    Box(modifier.fillMaxSize().background(seedBrush(seed)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(iconSize))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun BoxScope.WatchMarks(progress: Float?, completed: Boolean) {
    if (completed) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(palette.gradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Vista", tint = palette.onAccent, modifier = Modifier.size(15.dp))
        }
    } else if (progress != null && progress > 0f) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp)
        ) { ProgressBar(progress) }
    }
}

/** 16:9 card of a video or an episode. */
@Composable
fun WideTile(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    completed: Boolean = false,
    badge: String? = null,
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
                .aspectRatio(16f / 9f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
        ) {
            Art(imageUrl, title, placeholder)
            WatchMarks(progress, completed)
            if (focused) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            if (badge != null) {
                Badge(badge, Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = if (progress != null && !completed) 18.dp else 7.dp))
            }
        }
        CardCaption(title, subtitle, focused)
    }
}

@Composable
fun FolderTile(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        FocusCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused }
        ) {
            Box(Modifier.fillMaxSize().background(seedBrush(name)), contentAlignment = Alignment.Center) {
                if (emoji != null) Text(emoji, style = MaterialTheme.typography.displaySmall)
                else Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (focused) palette.accent else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        CardCaption(name, subtitle, focused)
    }
}

/**
 * Avatars are square and small, so the picture is shown round and, very dim,
 * as the light of the whole tile.
 */
@Composable
fun ChannelTile(
    channel: ChannelDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {}
) {
    val image = rememberMediaUrls().channelImage(channel.id)
    val name = channel.name.orEmpty()
    var focused by remember { mutableStateOf(false) }
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        corner = 16.dp,
        focusedScale = 1.04f,
        modifier = modifier.fillMaxWidth().height(86.dp).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
    ) {
        Box(Modifier.fillMaxSize().background(seedBrush(name)).alpha(0.9f))
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (focused) 0.5f else 0.28f)
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xD9080C15), Color(0x80080C15)))))
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChannelAvatar(channel.id, name, 54.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = if (focused) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900) else Modifier
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (channel.isFavorite) {
                        Icon(Icons.Filled.Star, contentDescription = "Favorito", tint = Star, modifier = Modifier.size(13.dp))
                    }
                    if (channel.isHidden) {
                        Icon(Icons.Outlined.VisibilityOff, contentDescription = "Oculto", tint = OnSurfaceMuted, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        channel.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun ChannelDto.summary(): String = listOfNotNull(
    videoCount?.takeIf { it > 0 }?.let { if (it == 1L) "1 vídeo" else "$it vídeos" },
    totalSizeText?.takeIf { it.isNotBlank() },
    if (videoCount == null) (if (isOwner) "Propio" else "Compartido") else null
).joinToString(" · ")

@Composable
fun ChannelAvatar(id: Long, name: String, size: Dp, modifier: Modifier = Modifier) {
    val urls = rememberMediaUrls()
    Box(
        modifier.size(size).clip(CircleShape).background(seedBrush(name + id)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        AsyncImage(
            model = urls.channelImage(id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun ApiFileDto.subtitle(): String = listOfNotNull(
    Format.day(dateModified ?: dateCreated).takeIf { it.isNotBlank() },
    (sizeText ?: Format.bytes(size)).takeIf { it.isNotBlank() }
).joinToString(" · ")

/** The preview of a video, when the setting wants it and the server has one. */
@Composable
fun thumbnailOf(file: ApiFileDto): String? {
    val urls = rememberMediaUrls()
    return if (rememberThumbnailsEnabled()) urls.absolute(file.thumbnailUrl) else null
}
