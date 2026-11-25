package com.mateof.tfmtv.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mateof.tfmtv.ui.theme.palette
import kotlin.math.roundToLong

/**
 * The line of the video. It only draws and takes touches: the keys that move
 * it are handled by the player, which also moves it with the controls hidden.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    active: Boolean,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    onScrub: (Long) -> Unit = {},
    onCommit: () -> Unit = {}
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val lit = focused || active
    val height by animateDpAsState(if (lit) 8.dp else 4.dp, tween(140), label = "seekHeight")
    val fraction = if (durationMs > 0) (positionMs / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val colors = palette

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .then(if (focusable) Modifier.focusable(interactionSource = interactions) else Modifier)
    ) {
        val barWidth = maxWidth
        val widthPx = with(LocalDensity.current) { barWidth.toPx() }
        fun at(x: Float) = ((x / widthPx).coerceIn(0f, 1f) * durationMs).roundToLong()

        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(durationMs) {
                    detectTapGestures {
                        onScrub(at(it.x))
                        onCommit()
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { onScrub(at(it.x)) },
                        onDragEnd = onCommit,
                        onDragCancel = onCommit
                    ) { change, _ -> onScrub(at(change.position.x)) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val shape = RoundedCornerShape(50)
            Box(Modifier.fillMaxWidth().height(height).clip(shape).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(buffered).height(height).clip(shape).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fraction).height(height).clip(shape).background(colors.horizontal))
            if (lit) {
                Box(
                    Modifier
                        .offset(x = barWidth * fraction - 8.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

/**
 * How far one key-down of a seek moves.
 *
 * A press is ten seconds. Holding moves at a speed that grows with how long the
 * key has been down, and the speed is per second held rather than per repeat:
 * repeats arrive every 50 ms on one remote and every 100 on another.
 */
class HeldSeek {
    private var pressed = false
    private var downAt = 0L
    private var lastAt = 0L

    /** [repeat] is the key event's repeat count: zero for a fresh press. */
    fun step(repeat: Int, now: Long = System.currentTimeMillis()): Long {
        if (repeat == 0 || !pressed) {
            pressed = true
            downAt = now
            lastAt = now
            return PRESS_STEP_MS
        }
        val elapsed = (now - lastAt).coerceIn(0L, MAX_GAP_MS)
        lastAt = now
        val speed = when (now - downAt) {
            in 0L until 1_500L -> 30L
            in 1_500L until 4_000L -> 90L
            in 4_000L until 8_000L -> 240L
            else -> 480L
        }
        return elapsed * speed
    }

    private companion object {
        const val PRESS_STEP_MS = 10_000L

        /** A repeat that took longer than this is not counted as time held. */
        const val MAX_GAP_MS = 600L
    }
}
