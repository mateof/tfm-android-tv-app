package com.mateof.tfmtv.cast

import android.content.Context
import com.mateof.tfmtv.cast.relay.CastRelay
import com.mateof.tfmtv.cast.relay.CastRelayService
import com.mateof.tfmtv.cast.relay.NetworkInspector
import com.mateof.tfmtv.cast.relay.RelayEnded
import com.mateof.tfmtv.data.prefs.CastRouteMode
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.WatchRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What is being sent. [url] is absolute, on the configured server, with the API key. */
data class CastRequest(
    val title: String,
    val url: String,
    val fileName: String,
    /** Identify the file whose progress is kept on the server; 0/"" disables it. */
    val channelId: Long,
    val fileId: String,
    /** Where to start; the television is told to seek there once it plays. */
    val startMs: Long = 0,
    /** Overrides the route in the settings: "try through this device" after Directo failed. */
    val route: CastRouteMode? = null
) {
    val mime: String get() = Dlna.mimeOf(fileName)
}

/** How a send went out, for the sheet to say so. */
data class CastOutcome(
    val route: String,
    /** What the television answered to Play when it was wrong about it; see [CastController.relayed]. */
    val playWarning: String? = null
) {
    val viaPhone: Boolean get() = route != CastSource.ROUTE_DIRECT
}

/**
 * Sends to a television by whichever route can work — directly from the server,
 * or through the relay on this device when the television cannot reach the
 * server itself (the server behind a VPN only this device is on) — and then
 * follows it: what the television says it is doing, where it is, the orders
 * from the panel, and the progress, which goes to the server as with the
 * internal player.
 */
@Singleton
class CastController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val upnp: UpnpClient,
    private val relay: CastRelay,
    private val network: NetworkInspector,
    private val prefs: ServerPreferences,
    private val monitor: CastMonitor,
    private val watch: WatchRepository,
    private val log: CastLog
) {
    private val memory = RouteMemory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One send at a time: a resend must not cross one the user just asked for. */
    private val sending = Mutex()
    private var autoJob: Job? = null
    private var watchJob: Job? = null

    @Volatile
    private var autoAttempts = 0

    /** Screens showing the panel; the television is asked more often while someone looks. */
    private val viewers = AtomicInteger(0)

    /** The furthest point seen while playing: a television that stops at the end often says 0:00:00. */
    @Volatile
    private var lastGoodElapsed = 0L

    private val _ended = MutableSharedFlow<RelayEnded>(extraBufferCapacity = 4)

    /** Sends that stopped for good. */
    val ended: SharedFlow<RelayEnded> = _ended.asSharedFlow()

    val session: StateFlow<CastSession?> = monitor.session

    init {
        scope.launch {
            relay.ended.collect { ended ->
                // After any send in flight: it may be the one that just ended.
                sending.withLock { }
                onRelayEnded(ended)
            }
        }
    }

    suspend fun send(device: CastDevice, request: CastRequest): Result<CastOutcome> {
        cancelAutomatic()
        log.clear()
        log.i("Enviando «${request.title}» a ${device.name} (${device.host})")
        return deliver(device, request, resumed = false)
    }

    /** Sends the same video again to the same television, from where it was. */
    suspend fun reconnect(): Result<CastOutcome> {
        val session = monitor.session.value
            ?: return Result.failure(IllegalStateException("no hay ningún envío que reconectar"))
        cancelAutomatic()
        log.i("Reconectar «${session.request.title}» en ${session.device.name}, a petición del usuario")
        monitor.reconnecting(0, null)
        return deliver(session.device, session.request.copy(startMs = resumePoint(session)), resumed = true)
            .onFailure { failure ->
                val hint = CastHints.of(failure)
                monitor.stopped(failure.message, hint)
                _ended.tryEmit(RelayEnded(session.device, failure.message, hint, session.generation))
            }
    }

    fun reconnectInBackground() {
        scope.launch { reconnect() }
    }

    /** Stops whatever is on the television, keeping the progress. */
    fun stopInBackground() {
        scope.launch { stopCurrent() }
    }

    suspend fun stopCurrent() {
        val session = monitor.session.value ?: return
        cancelAutomatic()
        watchJob?.cancel()
        if (session.phase == CastPhase.ACTIVE) saveProgress(session)
        monitor.clear()
        upnp.stop(session.device)
        relay.close("envío detenido")
        _ended.tryEmit(RelayEnded(session.device, null, null, session.generation, byUser = true))
    }

    /** Forgets a send that stopped by itself, once the user has seen it. */
    fun dismiss() {
        if (monitor.session.value?.phase == CastPhase.ENDED) monitor.clear()
    }

    fun togglePause() {
        val session = active() ?: return
        if (session.paused) resume() else pause()
    }

    fun pause() {
        val session = active() ?: return
        scope.launch {
            upnp.pause(session.device).onSuccess {
                monitor.assume("PAUSED_PLAYBACK")
                saveProgress(session)
            }
        }
    }

    fun resume() {
        val session = active() ?: return
        scope.launch { upnp.resume(session.device).onSuccess { monitor.assume("PLAYING") } }
    }

    fun seekBy(deltaMs: Long) {
        val session = active() ?: return
        val from = session.elapsedAt(System.currentTimeMillis()) ?: return
        seekTo(from + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        val session = active() ?: return
        val duration = session.position?.durationMs
        val target = positionMs.coerceAtLeast(0).let { if (duration != null) it.coerceAtMost(duration - 1_000) else it }
        scope.launch {
            log.i("Saltar a ${UpnpClient.clock(target)}")
            upnp.seek(session.device, target).onSuccess { monitor.seekedTo(target) }
        }
    }

    /** The panel is showing; the television is asked every couple of seconds while it is. */
    fun watching(on: Boolean) {
        if (on) viewers.incrementAndGet() else viewers.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    private fun active(): CastSession? = monitor.session.value?.takeIf { it.phase == CastPhase.ACTIVE }

    private suspend fun deliver(device: CastDevice, request: CastRequest, resumed: Boolean): Result<CastOutcome> =
        withContext(Dispatchers.IO) {
            sending.withLock {
                if (!resumed) {
                    monitor.clear()
                    lastGoodElapsed = 0
                }
                watchJob?.cancel()
                runCatching {
                    relay.close(if (resumed) "se reenvía a ${device.name}" else "nuevo envío a ${device.name}")
                    when (request.route ?: prefs.castRoute.value) {
                        CastRouteMode.DIRECT -> onlyDirect(device, request)
                        CastRouteMode.RELAY -> relayed(device, request, CastSource.ROUTE_RELAY)
                        CastRouteMode.AUTO -> automatic(device, request)
                    }
                }
                    .onSuccess { outcome ->
                        CastRelayService.clearEndNotice(context)
                        monitor.started(device, request, outcome, resumed)
                        val connectedAt = monitor.session.value?.connectedAt ?: 0L
                        watchJob = scope.launch { follow(connectedAt) }
                        if (request.startMs > 0) scope.launch { seekWhenPlaying(device, request.startMs, connectedAt) }
                        CastRelayService.start(context)
                    }
                    .onFailure { log.e("No se pudo enviar «${request.title}» a ${device.name}", it.message) }
            }
        }

    /**
     * Follows a send: feeds the panel, asks the television what it is doing,
     * keeps the progress on the server, and decides when it is over.
     */
    private suspend fun follow(connectedAt: Long) {
        var lastPoll = 0L
        var lastSave = System.currentTimeMillis()
        var stoppedPolls = 0
        var sawPlaying = false
        while (scope.isActive) {
            delay(TICK_MS)
            val session = monitor.session.value ?: return
            if (session.connectedAt != connectedAt || session.phase != CastPhase.ACTIVE) return
            monitor.sample(TICK_MS)
            val now = System.currentTimeMillis()

            val every = if (viewers.get() > 0) POLL_VISIBLE_MS else POLL_BACKGROUND_MS
            if (now - lastPoll >= every) {
                lastPoll = now
                val state = upnp.transportState(session.device)
                val answered = state.getOrNull()
                if (state.isSuccess) {
                    monitor.tvState(answered)
                    // A stopped television answers 0:00:00, which would wipe the
                    // position on the panel and on the server.
                    if (answered == "PLAYING" || answered == "PAUSED_PLAYBACK") {
                        upnp.position(session.device).onSuccess { position ->
                            monitor.position(position)
                            position.elapsedMs?.takeIf { it > 0 }?.let { lastGoodElapsed = it }
                        }
                    }
                } else if (now - session.lastAnswerAt > SILENT_LIMIT_MS) {
                    endWith(session, "la tele dejó de responder", CastHints.TV_GONE)
                    return
                }
                when (answered) {
                    "PLAYING" -> {
                        sawPlaying = true
                        stoppedPolls = 0
                    }
                    "STOPPED", "NO_MEDIA_PRESENT" -> {
                        // webOS holds a send in TRANSITIONING for half a minute
                        // and may say STOPPED on the way in.
                        stoppedPolls++
                        val settled = sawPlaying || now - connectedAt > STOPPED_GRACE_MS
                        if (stoppedPolls >= STOPPED_POLLS && settled) {
                            log.i("${session.device.name} ha dejado de reproducir (${tvStateLabel(answered)})")
                            finished(session)
                            return
                        }
                    }
                    null -> Unit
                    else -> stoppedPolls = 0
                }
            }

            if (session.viaPhone && relay.running(session.generation)) {
                val idle = relay.idleMs() ?: 0
                val tvState = monitor.session.value?.tvState
                val holding = tvState == "PLAYING" || tvState == "PAUSED_PLAYBACK" || tvState == "TRANSITIONING"
                if (idle > CUT_QUIET_MS && relay.cutAndAbandoned() && tvState != "PAUSED_PLAYBACK") {
                    relay.end("el servidor cortó el envío y la tele no volvió a pedirlo", recoverable = true)
                    return
                }
                // A paused television may hang up and only ask again on Play:
                // only a quiet relay *and* a television not holding the video ends it.
                if (idle > IDLE_LIMIT_MS && !holding) {
                    log.i("La tele lleva ${idle / 1000} s sin pedir el vídeo")
                    relay.end(null)
                    return
                }
            }

            if (now - lastSave >= SAVE_EVERY_MS) {
                lastSave = now
                saveProgress(monitor.session.value ?: session)
            }
        }
    }

    /**
     * A file starts at the beginning; the television is told to jump once it
     * plays. Before that most renderers refuse a Seek.
     */
    private suspend fun seekWhenPlaying(device: CastDevice, startMs: Long, connectedAt: Long) {
        val deadline = System.currentTimeMillis() + RESUME_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(1_500)
            val session = monitor.session.value ?: return
            if (session.connectedAt != connectedAt || session.phase != CastPhase.ACTIVE) return
            val state = upnp.transportState(device).getOrNull()
            if (state == "PLAYING" || state == "PAUSED_PLAYBACK") {
                log.i("Reanudando en ${UpnpClient.clock(startMs)}")
                upnp.seek(device, startMs).onSuccess { monitor.seekedTo(startMs) }
                return
            }
        }
        log.w("La tele no llegó a reproducir a tiempo; no se pudo reanudar en ${UpnpClient.clock(startMs)}")
    }

    /** The television stopped by itself: usually the end of the film. */
    private suspend fun finished(session: CastSession) {
        val duration = session.position?.durationMs ?: 0
        val completed = duration > 0 && lastGoodElapsed >= duration * COMPLETED_RATIO
        saveProgress(session, completed)
        relay.close("la tele ha terminado")
        monitor.stopped(null, null)
        _ended.tryEmit(RelayEnded(session.device, null, null, session.generation))
    }

    private suspend fun endWith(session: CastSession, problem: String, hint: String) {
        saveProgress(session)
        relay.close(problem)
        monitor.stopped(problem, hint)
        _ended.tryEmit(RelayEnded(session.device, problem, hint, session.generation))
    }

    private suspend fun saveProgress(session: CastSession, completed: Boolean = false) {
        val request = session.request
        if (request.channelId == 0L || request.fileId.isBlank()) return
        val duration = session.position?.durationMs ?: 0
        val elapsed = (session.elapsedAt(System.currentTimeMillis()) ?: 0).takeIf { it > 0 } ?: lastGoodElapsed
        // Position 0 would wipe a good one on the server.
        if (elapsed <= 0 && !completed) return
        val done = completed || (duration > 0 && elapsed >= duration * COMPLETED_RATIO)
        watch.save(request.channelId, request.fileId, elapsed, duration, if (done) true else null)
    }

    private fun resumePoint(session: CastSession): Long =
        (session.elapsedAt(System.currentTimeMillis()) ?: 0).takeIf { it > 0 } ?: lastGoodElapsed

    private fun onRelayEnded(ended: RelayEnded) {
        if (ended.byUser) {
            cancelAutomatic()
            monitor.clear()
            _ended.tryEmit(ended)
            return
        }
        val session = monitor.session.value
        if (session == null || session.generation != ended.generation || session.phase != CastPhase.ACTIVE) return
        watchJob?.cancel()
        if (System.currentTimeMillis() - session.connectedAt > STABLE_MS) autoAttempts = 0
        if (ended.recoverable && autoAttempts < MAX_AUTO_ATTEMPTS) {
            autoJob = scope.launch { reconnectByItself(session, ended) }
            return
        }
        scope.launch { saveProgress(session) }
        monitor.stopped(ended.problem, ended.hint)
        _ended.tryEmit(ended)
    }

    /**
     * Sends again after the server side failed: this device lost it for a
     * moment (a VPN reconnecting), or it cut the response and the television
     * gave up. From where it was, so the film carries on.
     */
    private suspend fun reconnectByItself(session: CastSession, ended: RelayEnded) {
        var reason = ended.problem
        var hint = ended.hint
        val request = session.request.copy(startMs = resumePoint(session))
        while (autoAttempts < MAX_AUTO_ATTEMPTS) {
            autoAttempts++
            monitor.reconnecting(autoAttempts, reason)
            delay(AUTO_RETRY_DELAY_MS)
            val failure = deliver(session.device, request, resumed = true).exceptionOrNull() ?: return
            reason = failure.message
            hint = CastHints.of(failure)
        }
        log.w("Se deja de reconectar con ${session.device.name} tras $MAX_AUTO_ATTEMPTS intentos")
        monitor.stopped(reason, hint)
        _ended.tryEmit(RelayEnded(session.device, reason, hint, session.generation))
    }

    private fun cancelAutomatic() {
        autoJob?.cancel()
        autoJob = null
        autoAttempts = 0
    }

    private suspend fun automatic(device: CastDevice, request: CastRequest): CastOutcome {
        val hostName = hostOf(request.url)
        val host = hostName?.let { resolve(it) }
        val key = RouteMemory.Key(device.udn, hostName.orEmpty())
        val remembered = memory[key]

        val wanted = when {
            remembered != null -> {
                log.i("Automático: ${device.name} + $hostName ya funcionó ${if (remembered == CastRoute.RELAY) "a través de este aparato" else "en directo"}")
                remembered
            }
            host == null -> {
                log.i("Automático: no se pudo resolver $hostName; se va directo")
                CastRoute.DIRECT
            }
            else -> {
                val tv = resolve(device.host)
                val decision = CastRouting.decide(
                    host = host,
                    tvSubnet = tv?.let { network.lanLinkFor(it)?.subnet },
                    hostViaVpn = CastRouting.viaVpn(network.routes(), host)
                )
                log.i(
                    "Automático: ${if (decision.route == CastRoute.RELAY) "a través de este aparato" else "directo"}",
                    "porque ${decision.reason}"
                )
                decision.route
            }
        }

        if (wanted == CastRoute.RELAY) {
            try {
                return relayed(device, request, CastSource.ROUTE_RELAY).also { memory[key] = CastRoute.RELAY }
            } catch (unavailable: RelayUnavailable) {
                // The guess said relay but this device cannot offer one from
                // here; the direct route might still work, and costs nothing to try.
                log.w("El puente no está disponible; se prueba directo", unavailable.message)
            }
        }

        return try {
            direct(device, request).also { memory[key] = CastRoute.DIRECT }
        } catch (failure: Exception) {
            if (!CastRouting.shouldFallBack(failure)) throw failure
            log.w("Directo falló; se reintenta a través de este aparato", failure.message)
            try {
                relayed(device, request, CastSource.ROUTE_FALLBACK).also { memory[key] = CastRoute.RELAY }
            } catch (unavailable: RelayUnavailable) {
                throw CastFailure(failure, unavailable)
            }
        }
    }

    /** Directo, as chosen in the settings: no fallback, but a clear reason and an offer. */
    private suspend fun onlyDirect(device: CastDevice, request: CastRequest): CastOutcome = try {
        direct(device, request)
    } catch (failure: Exception) {
        if (!CastRouting.shouldFallBack(failure)) throw failure
        val relayWorked = memory[RouteMemory.Key(device.udn, hostOf(request.url).orEmpty())] == CastRoute.RELAY
        throw DirectRefused(failure, relayWorked)
    }

    private suspend fun direct(device: CastDevice, request: CastRequest): CastOutcome {
        val source = CastSource(request.url, request.mime, Dlna.FILE_FEATURES, CastSource.ROUTE_DIRECT)
        upnp.play(device, source, request.title).getOrThrow()
        return CastOutcome(CastSource.ROUTE_DIRECT)
    }

    private suspend fun relayed(device: CastDevice, request: CastRequest, route: String): CastOutcome {
        val tv = resolve(device.host) ?: throw RelayUnavailable(
            "no se sabe la dirección de ${device.name}",
            CastHints.NO_LAN
        )
        val url = relay.open(
            device = device,
            tv = tv,
            upstreamHost = hostOf(request.url)?.let { resolve(it) },
            upstreamUrl = request.url,
            mime = request.mime,
            features = Dlna.FILE_FEATURES,
            title = request.title
        )
        val generation = relay.generation
        val source = CastSource(url, request.mime, Dlna.FILE_FEATURES, route)
        val play = scope.async { upnp.play(device, source, request.title) }
        // webOS holds Play open until its player settles and may answer an
        // error while it is already showing the video. What the television
        // does with the relay says more than what it answers.
        while (!play.isCompleted && !relay.tvReading(PLAYBACK_EVIDENCE_BYTES) && relay.running(generation)) {
            delay(EVIDENCE_POLL_MS)
        }
        if (!relay.running(generation)) {
            val ended = relay.lastEnded?.takeIf { it.generation == generation }
            throw RelayStopped(ended?.problem ?: "el puente se detuvo", ended?.hint)
        }
        var warning: String? = null
        if (play.isCompleted) {
            play.await().onFailure { error ->
                if (!keepsReading()) {
                    relay.close("${device.name} rechazó la dirección del puente")
                    throw error
                }
                warning = error.message
            }
        } else {
            log.i("${device.name} ya está recibiendo el vídeo; no se espera a que conteste a Play")
            scope.launch { lateAnswer(device, play, generation) }
        }
        return CastOutcome(route, warning)
    }

    private suspend fun lateAnswer(device: CastDevice, play: Deferred<Result<Unit>>, generation: Int) {
        play.await().onFailure { error ->
            if (relay.generation != generation) return
            if (keepsReading()) {
                log.w("${device.name} contestó a Play con un error, pero sigue recibiendo el vídeo", error.message)
            } else {
                relay.endIfStill(generation, "${device.name} no consiguió reproducir: ${error.message}", CastHints.of(error))
            }
        }
    }

    /**
     * Whether the television goes on reading after an error from Play. Asked
     * twice, a moment apart: one that gives up sends the error first and hangs
     * up right after.
     */
    private suspend fun keepsReading(): Boolean {
        if (!relay.tvReading()) return false
        delay(READING_GRACE_MS)
        return relay.tvReading()
    }

    private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun resolve(host: String): InetAddress? =
        runCatching { InetAddress.getByName(host) }
            .onFailure { log.w("No se pudo resolver $host", it.message) }
            .getOrNull()

    companion object {
        const val MAX_AUTO_ATTEMPTS = 3

        private const val TICK_MS = 1_000L
        private const val POLL_VISIBLE_MS = 2_000L
        private const val POLL_BACKGROUND_MS = 10_000L
        private const val SAVE_EVERY_MS = 30_000L
        private const val SILENT_LIMIT_MS = 45_000L
        private const val STOPPED_POLLS = 2
        private const val STOPPED_GRACE_MS = 60_000L
        private const val RESUME_WAIT_MS = 60_000L
        private const val COMPLETED_RATIO = 0.92

        /** Long enough for a television to come back after a seek or a hiccup. */
        private const val IDLE_LIMIT_MS = 60_000L

        /** After the server cut the response: a television that means to ask again does so at once. */
        private const val CUT_QUIET_MS = 8_000L

        /** A couple of seconds of HD: more than a renderer reads just to sniff the format. */
        private const val PLAYBACK_EVIDENCE_BYTES = 2L * 1024 * 1024
        private const val EVIDENCE_POLL_MS = 250L
        private const val READING_GRACE_MS = 3_000L
        private const val AUTO_RETRY_DELAY_MS = 5_000L

        /** Running this long since the last (re)send earns the automatic attempts back. */
        private const val STABLE_MS = 2 * 60_000L
    }
}
