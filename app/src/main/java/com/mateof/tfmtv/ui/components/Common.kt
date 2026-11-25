package com.mateof.tfmtv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.Surface
import com.mateof.tfmtv.ui.theme.SurfaceVariant
import com.mateof.tfmtv.ui.theme.palette
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Left edge of the content: the collapsed rail plus a margin. */
val ContentStart = 36.dp
val ContentEnd = 40.dp

@Composable
fun Loading(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = palette.accent,
                trackColor = Color.White.copy(alpha = 0.08f),
                strokeWidth = 3.dp
            )
            label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted) }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {}
) {
    CenteredNotice(Icons.Outlined.CloudOff, message, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onRetry != null) PillButton("Reintentar", Icons.Outlined.Refresh, onRetry, primary = true)
            extra()
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.VideoLibrary) {
    CenteredNotice(icon, message, modifier)
}

@Composable
private fun CenteredNotice(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(48.dp).widthIn(max = 520.dp)
        ) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(34.dp))
            }
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceMuted
            )
            actions()
        }
    }
}

/** A block that breathes while its content is on the way. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val alpha = if (palette.effects) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonAlpha"
        )
    } else remember { mutableStateOf(0.6f) }
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(corner))
            .background(SurfaceVariant)
    )
}

/** A row of cards that are still loading. */
@Composable
fun SkeletonRow(cardWidth: Dp, ratio: Float, modifier: Modifier = Modifier, count: Int = 6) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.size(width = 150.dp, height = 14.dp), corner = 6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(count) { Skeleton(Modifier.size(width = cardWidth, height = cardWidth / ratio)) }
        }
    }
}

@Composable
fun SkeletonGrid(columns: Int, ratio: Float, modifier: Modifier = Modifier, rows: Int = 3) {
    Column(
        modifier.fillMaxSize().padding(start = ContentStart, end = ContentEnd, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(columns) { Skeleton(Modifier.weight(1f).aspectRatio(ratio)) }
            }
        }
    }
}

/** Thin horizontal bar; [progress] in 0..1. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val fill = palette.horizontal
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(fill))
    }
}

/** A short notice over the bottom of the screen that leaves on its own. */
@Composable
fun BoxScope.MessageBar(message: String?, onDone: () -> Unit) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        shown = message
        delay(MESSAGE_MS)
        onDone()
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp, start = 80.dp, end = 80.dp)
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceVariant)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(palette.accent))
            Text(shown, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val MESSAGE_MS = 4_500L

/** Small label over a picture: a duration, a resolution, a count. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (strong) palette.onAccent else Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (strong) palette.accent else Color(0xB3000000))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun Clock(modifier: Modifier = Modifier) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(5_000)
        }
    }
    Text(
        now.format(clockFormat),
        style = MaterialTheme.typography.titleMedium,
        color = OnSurfaceMuted,
        modifier = modifier
    )
}

/** Title of a section, with what it holds and its controls on the right. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(start = ContentStart, end = ContentEnd, top = 24.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Rows of a list: lighter and with a border of the accent when focused, never a block of colour. */
@Composable
fun listRowColors(highlighted: Boolean = false) = androidx.tv.material3.ClickableSurfaceDefaults.colors(
    containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.06f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = Color.White.copy(alpha = 0.17f),
    focusedContentColor = Color.White,
    pressedContainerColor = Color.White.copy(alpha = 0.24f),
    pressedContentColor = Color.White
)

@Composable
fun listRowBorder(corner: Dp = 14.dp) = androidx.tv.material3.ClickableSurfaceDefaults.border(
    focusedBorder = androidx.tv.material3.Border(
        androidx.compose.foundation.BorderStroke(2.dp, palette.gradient),
        shape = RoundedCornerShape(corner)
    )
)

/** A panel that groups things over the background. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface.copy(alpha = 0.82f))
            .padding(18.dp)
    ) { content() }
}

/** Compose may not have placed the target yet when a screen opens: keep trying for a moment. */
suspend fun FocusRequester.requestSoon(times: Int = 15, every: Long = 50) {
    repeat(times) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(every)
    }
}
