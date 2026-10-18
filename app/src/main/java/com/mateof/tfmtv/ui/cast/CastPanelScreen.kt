package com.mateof.tfmtv.ui.cast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Replay30
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.cast.CastController
import com.mateof.tfmtv.cast.CastEvent
import com.mateof.tfmtv.cast.CastLog
import com.mateof.tfmtv.cast.CastPhase
import com.mateof.tfmtv.cast.CastSession
import com.mateof.tfmtv.cast.UpnpClient
import com.mateof.tfmtv.cast.tvStateLabel
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.ui.components.tapClick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CastPanelViewModel @Inject constructor(
    private val cast: CastController,
    log: CastLog
) : ViewModel() {
    val session = cast.session
    val events = log.events

    fun visible(on: Boolean) = cast.watching(on)
    fun togglePause() = cast.togglePause()
    fun seekBy(ms: Long) = cast.seekBy(ms)
    fun stop() = cast.stopInBackground()
    fun reconnect() = cast.reconnectInBackground()
    fun dismiss() = cast.dismiss()
}

/**
 * Everything about the send in progress: a remote for the television, how the
 * video is reaching it, and the conversation with it line by line.
 */
@Composable
fun CastPanelScreen(onBack: () -> Unit) {
    val vm: CastPanelViewModel = hiltViewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()

    BackHandler { onBack() }
    DisposableEffect(Unit) {
        vm.visible(true)
        onDispose { vm.visible(false) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 32.dp)
    ) {
        Text("Envío a la tele", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        val current = session
        if (current == null) {
            Text(
                "No se está enviando nada. Mantén pulsado un vídeo (o usa «Enviar a la tele» en una ficha) para mandarlo a una tele por DLNA.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusSoon(focus) }
            Button(onClick = onBack, modifier = Modifier.focusRequester(focus).tapClick(onBack)) { Text("Volver") }
            if (events.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                EventsCard(events, limit = Int.MAX_VALUE)
            }
            return@Column
        }

        Header(current)
        Spacer(Modifier.height(16.dp))
        Remote(current, vm)
        Spacer(Modifier.height(14.dp))
        Actions(current, events, vm, onBack)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                TvCard(current)
                Spacer(Modifier.height(14.dp))
                ConnectionCard(current)
                Spacer(Modifier.height(14.dp))
                TracksCard()
            }
            Column(Modifier.weight(1f)) {
                SpeedCard(current)
                Spacer(Modifier.height(14.dp))
                EventsCard(events, limit = 60)
            }
        }
    }
}

@Composable
private fun Header(session: CastSession) {
    val (label, color) = when (session.phase) {
        CastPhase.ACTIVE -> "En ${session.device.name}" to MaterialTheme.colorScheme.primary
        CastPhase.RECONNECTING -> "Reconectando con ${session.device.name}…" to Color(0xFFFFB74D)
        CastPhase.ENDED ->
            (if (session.endProblem == null) "Terminado en ${session.device.name}" else "Se paró en ${session.device.name}") to
                if (session.endProblem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        session.request.title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (session.phase == CastPhase.ENDED) {
        session.endHint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        session.endProblem?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Play/pause, jumps and stop, plus where the television is in the film. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Remote(session: CastSession, vm: CastPanelViewModel) {
    val active = session.phase == CastPhase.ACTIVE
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(500)
            value = System.currentTimeMillis()
        }
    }
    val elapsed = session.elapsedAt(now)
    val duration = session.position?.durationMs

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(elapsed?.let(UpnpClient::clock) ?: "--:--", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = if (elapsed != null && duration != null && duration > 0) (elapsed.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(duration?.let(UpnpClient::clock) ?: "--:--", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(12.dp))

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) focusSoon(playFocus) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RemoteButton(Icons.Outlined.Replay30, "−30 s", active) { vm.seekBy(-30_000) }
        RemoteButton(Icons.Outlined.Replay10, "−10 s", active) { vm.seekBy(-10_000) }
        RemoteButton(
            if (session.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            if (session.paused) "Reanudar" else "Pausa",
            active,
            Modifier.focusRequester(playFocus),
            vm::togglePause
        )
        RemoteButton(Icons.Outlined.Forward10, "+10 s", active) { vm.seekBy(10_000) }
        RemoteButton(Icons.Outlined.Forward30, "+30 s", active) { vm.seekBy(30_000) }
        RemoteButton(Icons.Outlined.Stop, "Parar", session.phase != CastPhase.ENDED, onClick = vm::stop)
    }
}

@Composable
private fun RemoteButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.tapClick { if (enabled) onClick() },
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(session: CastSession, events: List<CastEvent>, vm: CastPanelViewModel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val ended = session.phase == CastPhase.ENDED
    LaunchedEffect(ended) { if (ended) focusSoon(focus) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_500)
            copied = false
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RemoteButton(
            Icons.Outlined.Refresh,
            "Reconectar",
            session.phase != CastPhase.RECONNECTING,
            if (ended) Modifier.focusRequester(focus) else Modifier,
            vm::reconnect
        )
        RemoteButton(Icons.Outlined.ContentCopy, if (copied) "Copiado" else "Copiar diagnóstico", true) {
            clipboard.setText(AnnotatedString(diagnostics(session, events)))
            copied = true
        }
        if (ended) Button(onClick = vm::dismiss, modifier = Modifier.tapClick(vm::dismiss)) { Text("Olvidar") }
        Button(onClick = onBack, modifier = Modifier.tapClick(onBack)) { Text("Volver") }
    }
    if (session.viaPhone && session.phase == CastPhase.ACTIVE) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Si este aparato pierde el servidor (p. ej. la VPN se reconecta), se reenvía solo hasta ${CastController.MAX_AUTO_ATTEMPTS} veces desde donde iba.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeedCard(session: CastSession) {
    Card("Velocidad") {
        if (!session.viaPhone) {
            Text(
                "La tele descarga el vídeo directamente del servidor; este aparato no ve el tráfico.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Card
        }
        Text(
            "${Format.bytes(session.rateBytes).ifBlank { "0 B" }}/s",
            style = MaterialTheme.typography.headlineSmall,
            color = if (session.tvConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (session.history.size >= 2) {
            Sparkline(session.history, Modifier.fillMaxWidth().height(56.dp))
        } else {
            Text("Esperando datos…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Figure("media", "${Format.bytes(session.averageBytes).ifBlank { "0 B" }}/s")
            Figure("servido", Format.bytes(session.servedBytes).ifBlank { "0 B" })
            session.totalBytes?.let { Figure("tamaño", Format.bytes(it)) }
        }
    }
}

@Composable
private fun TvCard(session: CastSession) {
    Card("Tele") {
        Field(session.device.name, null)
        session.device.model?.let { Field("Modelo", it) }
        Field("Dirección", session.device.host)
        Field("Estado", tvStateLabel(session.tvState))
        if (session.viaPhone) {
            Field("Conexión", if (session.tvConnected) "leyendo del puente" else "sin conexión abierta")
            Field("Peticiones", session.tvRequests.toString())
        }
        session.tvAgent?.let { Field("Reproductor", it) }
    }
}

@Composable
private fun ConnectionCard(session: CastSession) {
    Card("Ruta") {
        Field(
            "Camino",
            when {
                session.fallback -> "A través de este aparato (el directo falló)"
                session.viaPhone -> "A través de este aparato"
                else -> "Directo desde el servidor"
            }
        )
        session.link?.let { link ->
            Field("Este aparato", listOfNotNull(link.interfaceName, link.address + (link.port?.let { ":$it" } ?: "")).joinToString(" · "))
            Field("Red de la tele", link.subnet)
        }
        serverOf(session)?.let { Field("Servidor", it) }
        session.upstreamMs?.let { Field("Respuesta del servidor", "$it ms") }
        Field("Reenvíos", session.resends.toString())
    }
}

/**
 * DLNA has no order for the audio track or the subtitles: they are the
 * television's own player's business. Said here so nobody looks for a button.
 */
@Composable
private fun TracksCard() {
    Card("Idioma y subtítulos") {
        Text(
            "DLNA no tiene órdenes para cambiar el idioma ni los subtítulos. Usa el mando de la tele: " +
                "en LG y Samsung, el botón de ajustes del reproductor (⚙ o «…») durante la reproducción " +
                "permite elegir pista de audio y subtítulos incrustados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EventsCard(events: List<CastEvent>, limit: Int) {
    Card("Comunicación") {
        if (events.isEmpty()) {
            Text("Sin novedades.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Card
        }
        events.take(limit).forEach { event ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    timeOf(event.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp).padding(top = 2.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        event.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (event.level) {
                            CastEvent.Level.INFO -> MaterialTheme.colorScheme.onSurface
                            CastEvent.Level.WARN -> Color(0xFFFFB74D)
                            CastEvent.Level.ERROR -> MaterialTheme.colorScheme.error
                        }
                    )
                    event.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * A section of the panel. Focusable although there is nothing to press: with
 * the remote the arrows only scroll by moving focus, and without a target the
 * lower cards could never be reached.
 */
@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row(Modifier.padding(vertical = 2.dp)) {
        if (value == null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            return@Row
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(160.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The last minute of speed, scaled to its own peak: the shape tells a stall from a dip. */
@Composable
private fun Sparkline(history: List<Long>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val peak = (history.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val step = size.width / (history.size - 1).coerceAtLeast(1)
        fun point(i: Int) = Offset(step * i, size.height - size.height * (history[i] / peak) * 0.9f)
        val stroke = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until history.size) lineTo(point(i).x, point(i).y)
        }
        val fill = Path().apply {
            addPath(stroke)
            lineTo(point(history.lastIndex).x, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, line.copy(alpha = 0.16f))
        drawPath(stroke, line, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Host and port only: the query of the stream url carries the API key. */
private fun serverOf(session: CastSession): String? {
    val uri = runCatching { URI(session.request.url) }.getOrNull() ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    return if (uri.port > 0) "$host:${uri.port}" else host
}

/** The same panel as text, for pasting into a message when asking for help. */
private fun diagnostics(session: CastSession, events: List<CastEvent>): String = buildString {
    appendLine("TFM TV · envío a la tele")
    appendLine("${session.request.title} → ${session.device.name} (${session.device.host}${session.device.model?.let { ", $it" } ?: ""})")
    appendLine("Ruta: " + if (session.viaPhone) "a través de este aparato" + (if (session.fallback) " (tras fallar el directo)" else "") else "directo")
    appendLine("Estado de la tele: ${tvStateLabel(session.tvState)}")
    session.link?.let { appendLine("Puente: ${it.interfaceName ?: "?"} ${it.address}:${it.port ?: "?"} (${it.subnet})") }
    serverOf(session)?.let { appendLine("Servidor: $it" + (session.upstreamMs?.let { ms -> " · $ms ms" } ?: "")) }
    if (session.viaPhone) appendLine("Servido: ${Format.bytes(session.servedBytes)} · peticiones de la tele: ${session.tvRequests}")
    session.tvAgent?.let { appendLine("Reproductor de la tele: $it") }
    session.endProblem?.let { appendLine("Fin: $it") }
    appendLine()
    appendLine("Comunicación:")
    events.forEach { appendLine("${timeOf(it.at)}  ${it.text}" + (it.detail?.let { d -> " — $d" } ?: "")) }
}

private fun timeOf(epochMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(epochMillis))
