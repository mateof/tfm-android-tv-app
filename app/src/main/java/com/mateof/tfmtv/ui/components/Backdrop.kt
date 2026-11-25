package com.mateof.tfmtv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.mateof.tfmtv.ui.theme.Background
import com.mateof.tfmtv.ui.theme.palette
import kotlinx.coroutines.delay

/** [value] once it has stayed the same for a moment: the focus crosses many cards on its way. */
@Composable
private fun <T> settled(value: T, delayMs: Long): T {
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        delay(delayMs)
        shown = value
    }
    return shown
}

@Composable
private fun Fading(url: String?, modifier: Modifier, content: @Composable (String?) -> Unit) {
    if (palette.effects) {
        Crossfade(targetState = url, animationSpec = tween(650), label = "backdrop", modifier = modifier) { content(it) }
    } else Box(modifier) { content(url) }
}

/**
 * The picture of what has the focus, at the right and top. The picture itself
 * fades out, so it melts into whatever is behind it without an edge.
 */
@Composable
fun HeroBackdrop(url: String?, modifier: Modifier = Modifier, height: Float = 0.78f, settleMs: Long = 260) {
    val shown = settled(url, settleMs)
    Box(modifier.fillMaxSize()) {
        Fading(shown, Modifier.fillMaxWidth().fillMaxHeight(height)) { current ->
            if (current != null) {
                AsyncImage(
                    model = current,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.horizontalGradient(0.12f to Color.Transparent, 0.62f to Color.Black),
                                blendMode = BlendMode.DstIn
                            )
                            drawRect(
                                Brush.verticalGradient(0.45f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }
        }
        // Keeps the text on the left readable over any picture.
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(
                Brush.horizontalGradient(listOf(Background.copy(alpha = 0.7f), Color.Transparent))
            )
        )
    }
}

/**
 * The colours of the focused poster over the whole screen. It is a tiny
 * picture stretched a lot: a blur that costs nothing on a slow stick.
 */
@Composable
fun AmbientBackdrop(tinyUrl: String?, modifier: Modifier = Modifier) {
    if (!palette.effects) return
    val shown = settled(tinyUrl, 320)
    Fading(shown, modifier.fillMaxSize()) { current ->
        if (current != null) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = current,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.34f)
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Background.copy(alpha = 0.55f),
                            1f to Background.copy(alpha = 0.92f)
                        )
                    )
                )
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(0.4f).background(
                        Brush.horizontalGradient(listOf(Background.copy(alpha = 0.8f), Color.Transparent))
                    )
                )
            }
        }
    }
}
