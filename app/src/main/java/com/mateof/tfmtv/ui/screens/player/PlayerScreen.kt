package com.mateof.tfmtv.ui.screens.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.ui.components.Art
import com.mateof.tfmtv.ui.components.Clock
import com.mateof.tfmtv.ui.components.IconPill
import com.mateof.tfmtv.ui.components.OptionPicker
import com.mateof.tfmtv.ui.components.PickerOption
import com.mateof.tfmtv.ui.components.PillButton
import com.mateof.tfmtv.ui.components.ProgressBar
import com.mateof.tfmtv.ui.components.requestSoon
import com.mateof.tfmtv.ui.theme.Background
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.palette
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What is drawn over the picture. SEEK is only the bar, while the arrows move it. */
private enum class Overlay { NONE, SEEK, FULL }

private enum class Picker { AUDIO, TEXT, SPEED, PICTURE }

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val pictures = listOf(
    PickerOption(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Ajustar", "Entera"),
    PickerOption(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom", "Recorta"),
    PickerOption(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Rellenar", "Estira")
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    channelId: Long = 0,
    fileId: String = "",
    startMs: Long = 0,
    onBack: () -> Unit
) {
    val vm: PlayerViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    /** Where the bar is being moved to; the video is still where it was. */
    var scrub by remember { mutableStateOf<Long?>(null) }
    var commitAt by remember { mutableLongStateOf(0L) }
    var barFocused by remember { mutableStateOf(false) }
    /** Any key or touch: puts off hiding the controls. */
    var touched by remember { mutableIntStateOf(0) }
    /** The end of this episode was already answered with "not now". */
    var dismissedNext by remember(state.next?.file?.fileId) { mutableStateOf(false) }
    val held = remember { HeldSeek() }
    val root = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }

    LaunchedEffect(url) { vm.prepare(url, title, channelId, fileId, startMs) }

    // Out of sight the video stops; nobody is watching it and it keeps the place.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.setPlaying(false)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val failed = state.error != null
    val remaining = if (state.durationMs > 0) state.durationMs - state.positionMs else Long.MAX_VALUE
    val offerNext = state.next != null && !dismissedNext && !failed && overlay == Overlay.NONE && picker == null &&
        (state.ended || (state.autoplay && state.durationMs > 3 * PlayerViewModel.NEXT_PROMPT_MS && remaining <= PlayerViewModel.NEXT_PROMPT_MS))

    // The end: the next episode, its offer, or out.
    LaunchedEffect(state.ended) {
        if (!state.ended) return@LaunchedEffect
        overlay = Overlay.NONE
        when {
            state.next == null || dismissedNext -> {
                delay(600)
                onBack()
            }
            state.autoplay -> vm.playNext()
        }
    }

    LaunchedEffect(overlay, failed) {
        when {
            failed -> Unit
            overlay == Overlay.FULL -> playButton.requestSoon()
            else -> root.requestSoon()
        }
    }
    // The panel takes the focus with it when it closes.
    LaunchedEffect(picker) {
        touched++
        if (picker == null && overlay == Overlay.FULL) {
            delay(60)
            playButton.requestSoon()
        }
    }
    LaunchedEffect(overlay, touched, state.playing, picker, scrub) {
        if (overlay != Overlay.FULL || picker != null || !state.playing || scrub != null) return@LaunchedEffect
        delay(HIDE_AFTER_MS)
        overlay = Overlay.NONE
    }

    fun commit() {
        scrub?.let(vm::seekTo)
        scrub = null
        commitAt = 0L
        if (overlay == Overlay.SEEK) overlay = Overlay.NONE
    }
    // Counted from the key-up, never from a key-down: the first repeat of a held
    // key arrives half a second after the press, and would move it again.
    LaunchedEffect(commitAt) {
        if (commitAt == 0L) return@LaunchedEffect
        delay(SEEK_SETTLE_MS)
        commit()
    }

    fun nudge(direction: Int, repeat: Int) {
        val from = scrub ?: state.positionMs
        val target = from + direction * held.step(repeat)
        scrub = if (state.durationMs > 0) target.coerceIn(0L, state.durationMs) else target.coerceAtLeast(0L)
        commitAt = 0L
        if (overlay == Overlay.NONE) overlay = Overlay.SEEK
    }

    fun onKey(event: KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        val repeat = event.nativeKeyEvent.repeatCount
        if (down) touched++
        when (event.key) {
            Key.MediaPlayPause, Key.Spacebar -> {
                if (down && repeat == 0) vm.togglePlay()
                return true
            }
            Key.MediaPlay -> { if (down) vm.setPlaying(true); return true }
            Key.MediaPause, Key.MediaStop -> { if (down) vm.setPlaying(false); return true }
            Key.MediaNext -> { if (down && repeat == 0) vm.playNext(); return true }
            Key.MediaPrevious -> { if (down && repeat == 0) vm.playPrevious(); return true }
        }
        if (failed) return false

        val back = event.key == Key.DirectionLeft || event.key == Key.MediaRewind
        val forth = event.key == Key.DirectionRight || event.key == Key.MediaFastForward
        val media = event.key == Key.MediaRewind || event.key == Key.MediaFastForward
        if ((back || forth) && (media || overlay != Overlay.FULL || barFocused)) {
            if (down) nudge(if (back) -1 else 1, repeat)
            else if (scrub != null) commitAt = System.currentTimeMillis()
            return true
        }
        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (ok && scrub != null) {
            if (!down) commit()
            return true
        }
        if (overlay == Overlay.FULL) {
            if (ok && barFocused) {
                if (!down) vm.togglePlay()
                return true
            }
            return false
        }
        return when {
            ok && offerNext -> { if (!down) vm.playNext(); true }
            ok -> {
                if (!down) {
                    vm.togglePlay()
                    overlay = Overlay.FULL
                }
                true
            }
            event.key == Key.DirectionUp || event.key == Key.DirectionDown || event.key == Key.Menu || event.key == Key.Info -> {
                if (down) overlay = Overlay.FULL
                true
            }
            else -> false
        }
    }

    BackHandler {
        when {
            failed -> onBack()
            scrub != null -> {
                scrub = null
                commitAt = 0L
                if (overlay == Overlay.SEEK) overlay = Overlay.NONE
            }
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            offerNext && !state.ended -> dismissedNext = true
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent(::onKey)
            .focusRequester(root)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = vm.player
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.setApplyEmbeddedStyles(true)
                }
            },
            update = { view -> view.resizeMode = state.resizeMode },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (it.x < size.width / 2) vm.seekBy(-10_000) else vm.seekBy(10_000) },
                    onTap = {
                        touched++
                        overlay = if (overlay == Overlay.FULL) Overlay.NONE else Overlay.FULL
                    }
                )
            }
        )

        if (state.buffering && !failed && !state.ended) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(52.dp),
                color = palette.accent,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeWidth = 4.dp
            )
        } else if (!state.wantsToPlay && overlay == Overlay.NONE && !failed && !state.ended) {
            Box(
                Modifier.align(Alignment.Center).size(76.dp).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Pause, contentDescription = "En pausa", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        AnimatedVisibility(state.notice != null && overlay != Overlay.FULL, enter = fadeIn(), exit = fadeOut()) {
            var shown by remember { mutableStateOf("") }
            state.notice?.let { shown = it }
            Text(
                text = shown,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .padding(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        Controls(
            state = state,
            overlay = overlay,
            scrub = scrub,
            playButton = playButton,
            onBarFocus = { barFocused = it },
            onScrub = { scrub = it },
            onCommit = ::commit,
            onTogglePlay = vm::togglePlay,
            onSeekBy = vm::seekBy,
            onNext = { vm.playNext() },
            onPrevious = { vm.playPrevious() },
            onPick = { picker = it }
        )

        NextOffer(
            visible = offerNext,
            next = state.next,
            secondsLeft = if (state.ended) null else (remaining / 1000).toInt().coerceAtLeast(0),
            onPlay = { vm.playNext() }
        )

        state.error?.let { message ->
            PlaybackError(
                message = message,
                title = state.title,
                onRetry = vm::retry,
                onElsewhere = { vm.openElsewhere() },
                onBack = onBack
            )
        }
    }

    when (picker) {
        Picker.AUDIO -> OptionPicker(
            title = "Audio",
            options = state.audio.map { PickerOption<PlayerTrack?>(it, it.label) },
            selected = state.audio.firstOrNull { it.selected },
            onSelect = { it?.let(vm::selectAudio) },
            onDismiss = { picker = null }
        )
        Picker.TEXT -> OptionPicker(
            title = "Subtítulos",
            options = listOf(PickerOption<PlayerTrack?>(null, "Sin subtítulos")) + state.text.map { PickerOption<PlayerTrack?>(it, it.label) },
            selected = state.text.firstOrNull { it.selected },
            onSelect = vm::selectText,
            onDismiss = { picker = null }
        )
        Picker.SPEED -> OptionPicker(
            title = "Velocidad",
            options = speeds.map { PickerOption(it, speedLabel(it)) },
            selected = state.speed,
            onSelect = vm::setSpeed,
            onDismiss = { picker = null }
        )
        Picker.PICTURE -> OptionPicker(
            title = "Imagen",
            options = pictures,
            selected = state.resizeMode,
            onSelect = { vm.setResizeMode(it) },
            onDismiss = { picker = null }
        )
        null -> Unit
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == 1f) "Normal" else "${"%.2f".format(speed).trimEnd('0').trimEnd(',', '.')}×"

@Composable
private fun BoxScope.Controls(
    state: PlayerState,
    overlay: Overlay,
    scrub: Long?,
    playButton: FocusRequester,
    onBarFocus: (Boolean) -> Unit,
    onScrub: (Long) -> Unit,
    onCommit: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPick: (Picker) -> Unit
) {
    val position = scrub ?: state.positionMs

    AnimatedVisibility(
        visible = overlay == Overlay.FULL,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 4 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 4 },
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(start = SIDE, end = SIDE, top = 28.dp, bottom = 46.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                Clock()
                if (state.durationMs > 0) {
                    val left = ((state.durationMs - position) / state.speed).toLong()
                    Text(
                        "Termina a las ${LocalTime.now().plusNanos(left * 1_000_000).format(clockFormat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = overlay != Overlay.NONE,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 4 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 4 },
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = SIDE, end = SIDE, top = 60.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(Format.duration(position), style = MaterialTheme.typography.titleSmall, color = Color.White)
                SeekBar(
                    positionMs = position,
                    bufferedMs = state.bufferedMs,
                    durationMs = state.durationMs,
                    active = scrub != null,
                    focusable = overlay == Overlay.FULL,
                    onScrub = onScrub,
                    onCommit = onCommit,
                    modifier = Modifier.weight(1f).onFocusChanged { onBarFocus(it.isFocused) }
                )
                Text(
                    if (state.durationMs > 0) "-" + Format.duration(state.durationMs - position) else "--:--",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            if (overlay == Overlay.FULL) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.previous != null) IconPill(Icons.Outlined.SkipPrevious, "Anterior", onPrevious)
                    IconPill(Icons.Outlined.Replay10, "−10 s", { onSeekBy(-10_000) })
                    IconPill(
                        icon = if (state.wantsToPlay && !state.ended) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (state.wantsToPlay && !state.ended) "Pausa" else "Reproducir",
                        onClick = onTogglePlay,
                        modifier = Modifier.focusRequester(playButton)
                    )
                    IconPill(Icons.Outlined.Forward30, "+30 s", { onSeekBy(30_000) })
                    state.next?.let { IconPill(Icons.Outlined.SkipNext, "Siguiente: ${it.label}".take(46), onNext) }
                    Spacer(Modifier.weight(1f))
                    if (state.audio.size > 1) IconPill(Icons.Outlined.Audiotrack, "Audio", { onPick(Picker.AUDIO) })
                    if (state.text.isNotEmpty()) {
                        IconPill(Icons.Outlined.Subtitles, "Subtítulos", { onPick(Picker.TEXT) }, selected = !state.textOff)
                    }
                    IconPill(Icons.Outlined.Speed, "Velocidad", { onPick(Picker.SPEED) }, selected = state.speed != 1f)
                    IconPill(
                        Icons.Outlined.AspectRatio, "Imagen", { onPick(Picker.PICTURE) },
                        selected = state.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT
                    )
                }
            }
        }
    }
}

/** Over the last seconds of an episode. It does not take the focus: OK plays it, BACK sends it away. */
@Composable
private fun BoxScope.NextOffer(visible: Boolean, next: NextEpisode?, secondsLeft: Int?, onPlay: () -> Unit) {
    AnimatedVisibility(
        visible = visible && next != null,
        enter = fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 3 },
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = SIDE, bottom = 40.dp)
    ) {
        val episode = next ?: return@AnimatedVisibility
        Column(
            Modifier
                .width(330.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF0101624))
                .pointerInput(episode) { detectTapGestures { onPlay() } }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(118.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                    Art(episode.imageUrl, episode.label, Icons.Outlined.Movie, iconSize = 24.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("SIGUIENTE EPISODIO", style = MaterialTheme.typography.labelMedium, color = palette.accent)
                    Text(episode.label, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (secondsLeft != null) {
                ProgressBar(1f - secondsLeft * 1000f / PlayerViewModel.NEXT_PROMPT_MS, height = 3.dp)
            }
            Text(
                if (secondsLeft != null) "Empieza en $secondsLeft s · OK para verlo ya · ATRÁS para quedarte"
                else "OK para verlo · ATRÁS para salir",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun PlaybackError(
    message: String,
    title: String,
    onRetry: () -> Unit,
    onElsewhere: () -> Unit,
    onBack: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestSoon() }
    Box(Modifier.fillMaxSize().background(Background.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Reintentar", Icons.Outlined.Refresh, onRetry, Modifier.focusRequester(focus), primary = true)
                PillButton("Abrir con otra app", Icons.AutoMirrored.Outlined.OpenInNew, onElsewhere)
                PillButton("Volver", Icons.AutoMirrored.Outlined.ArrowBack, onBack)
            }
        }
    }
}

private val SIDE = 48.dp
private const val HIDE_AFTER_MS = 4_500L
private const val SEEK_SETTLE_MS = 600L
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
