package com.mateof.tfmtv.cast

import com.mateof.tfmtv.cast.relay.CastRelay
import com.mateof.tfmtv.cast.relay.RelayStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class CastPhase { ACTIVE, RECONNECTING, ENDED }

/** How to word a UPnP transport state. */
fun tvStateLabel(state: String?): String = when (state) {
    null -> "Desconocido"
    "PLAYING" -> "Reproduciendo"
    "TRANSITIONING" -> "Cargando"
    "PAUSED_PLAYBACK" -> "En pausa"
    "STOPPED" -> "Detenida"
    "NO_MEDIA_PRESENT" -> "Sin vídeo"
    else -> state
}

/** This device's side of the relay: where the television fetches from. */
data class CastLinkInfo(
    val interfaceName: String?,
    val subnet: String,
    val address: String,
    val port: Int?
)

/** Everything known about the send in progress, or the last one if it stopped by itself. */
data class CastSession(
    val device: CastDevice,
    val request: CastRequest,
    val viaPhone: Boolean,
    /** Sent directly first, and through this device after the television could not fetch it. */
    val fallback: Boolean,
    val link: CastLinkInfo?,
    /** The relay session currently serving it; see [CastRelay.generation]. */
    val generation: Int,
    val startedAt: Long,
    /** The last time it was (re)sent. */
    val connectedAt: Long,
    val endedAt: Long? = null,
    val phase: CastPhase = CastPhase.ACTIVE,
    /** Which automatic attempt is running while RECONNECTING; 0 for one the user asked for. */
    val attempt: Int = 0,
    val tvState: String? = null,
    val position: CastPosition? = null,
    /** When [position] was read, to move the clock on between two readings. */
    val positionAt: Long = 0,
    val servedBytes: Long = 0,
    val rateBytes: Long = 0,
    val averageBytes: Long = 0,
    /** Bytes per second, a sample a second, oldest first. */
    val history: List<Long> = emptyList(),
    val tvConnected: Boolean = false,
    /** Requests the television made to the relay: the first, then one per seek or retry. */
    val tvRequests: Int = 0,
    /** Times it was sent again: by the user or automatically. */
    val resends: Int = 0,
    val tvAgent: String? = null,
    val upstreamMs: Long? = null,
    val totalBytes: Long? = null,
    /** The last time the television answered at all. */
    val lastAnswerAt: Long = 0,
    val endProblem: String? = null,
    val endHint: String? = null
) {
    val paused: Boolean get() = tvState == "PAUSED_PLAYBACK"

    /** Where the television is now, moved on from the last reading while it plays. */
    fun elapsedAt(now: Long): Long? {
        val elapsed = position?.elapsedMs ?: return null
        if (tvState != "PLAYING" || positionAt == 0L) return elapsed
        val moved = elapsed + (now - positionAt)
        return position.durationMs?.let { moved.coerceAtMost(it) } ?: moved
    }
}

/**
 * Holds what the transmission panel shows. Only records: deciding — sending
 * again, giving up — stays in [CastController], which tells this what happened.
 */
@Singleton
class CastMonitor @Inject constructor(
    private val relay: CastRelay,
    private val log: CastLog
) {
    private val _session = MutableStateFlow<CastSession?>(null)
    val session: StateFlow<CastSession?> = _session.asStateFlow()

    private val throughput = Throughput()

    /** Bytes served by relays that have since been replaced, so the total survives a resend. */
    private var carriedBytes = 0L
    private var lastRelayBytes = 0L
    private var activeMs = 0L

    /**
     * A send went out. [resumed] keeps what was gathered so far — the same
     * thing to the same television, sent again.
     */
    @Synchronized
    fun started(device: CastDevice, request: CastRequest, outcome: CastOutcome, resumed: Boolean) {
        val now = System.currentTimeMillis()
        val previous = _session.value?.takeIf { resumed }
        val running = relay.status.value as? RelayStatus.Running
        val link = running?.takeIf { outcome.viaPhone }?.link?.let {
            CastLinkInfo(
                interfaceName = it.interfaceName,
                subnet = "${it.subnet.address.hostAddress}/${it.subnet.prefixLength}",
                address = it.address.hostAddress.orEmpty(),
                port = relay.port()
            )
        }
        if (previous == null) {
            carriedBytes = 0
            activeMs = 0
            throughput.reset()
        } else {
            carriedBytes += lastRelayBytes
            throughput.rebase()
        }
        lastRelayBytes = 0
        val base = previous ?: CastSession(
            device = device,
            request = request,
            viaPhone = outcome.viaPhone,
            fallback = outcome.route == CastSource.ROUTE_FALLBACK,
            link = link,
            generation = relay.generation,
            startedAt = now,
            connectedAt = now
        )
        _session.value = base.copy(
            request = request,
            viaPhone = outcome.viaPhone,
            fallback = outcome.route == CastSource.ROUTE_FALLBACK,
            link = link,
            generation = relay.generation,
            connectedAt = now,
            lastAnswerAt = now,
            phase = CastPhase.ACTIVE,
            attempt = 0,
            resends = if (previous != null) previous.resends + 1 else 0,
            endProblem = null,
            endHint = null,
            endedAt = null
        )
        outcome.playWarning?.let { log.w("La tele contestó a Play con un error, pero está recibiendo el vídeo", it) }
    }

    fun reconnecting(attempt: Int, reason: String?) {
        _session.update { it?.copy(phase = CastPhase.RECONNECTING, attempt = attempt, tvConnected = false, rateBytes = 0) }
        log.w(
            if (attempt > 0) "Reconectando solo ($attempt/${CastController.MAX_AUTO_ATTEMPTS})" else "Reconectando",
            reason
        )
    }

    /** The send stopped and nothing more will be tried. */
    fun stopped(problem: String?, hint: String?) {
        _session.update {
            it?.copy(
                phase = CastPhase.ENDED,
                attempt = 0,
                tvConnected = false,
                rateBytes = 0,
                endProblem = problem,
                endHint = hint,
                endedAt = System.currentTimeMillis()
            )
        }
        if (problem != null) log.e("Envío detenido", problem) else log.i("Envío terminado")
    }

    /** Stopped on purpose, or replaced by another send: nothing left to show. */
    fun clear() {
        _session.value = null
    }

    fun tvState(state: String?) {
        val before = _session.value?.tvState
        _session.update { it?.copy(tvState = state, lastAnswerAt = System.currentTimeMillis()) }
        if (state != null && state != before) log.i("Tele: ${tvStateLabel(state)}")
    }

    fun position(position: CastPosition) {
        _session.update { it?.copy(position = position, positionAt = System.currentTimeMillis()) }
    }

    /** An order just changed the state, so the panel shows it before the next reading. */
    fun assume(state: String) {
        _session.update {
            val elapsed = it?.elapsedAt(System.currentTimeMillis())
            it?.copy(
                tvState = state,
                position = it.position?.copy(elapsedMs = elapsed),
                positionAt = System.currentTimeMillis()
            )
        }
    }

    fun seekedTo(ms: Long) {
        _session.update {
            it?.copy(
                position = (it.position ?: CastPosition(null, null)).copy(elapsedMs = ms),
                positionAt = System.currentTimeMillis()
            )
        }
    }

    @Synchronized
    fun sample(sampleMs: Long) {
        val current = _session.value ?: return
        if (current.phase != CastPhase.ACTIVE || !current.viaPhone) return
        val stats = relay.stats() ?: return
        if (!relay.running(current.generation)) return
        lastRelayBytes = stats.servedBytes
        val total = carriedBytes + stats.servedBytes
        val now = System.currentTimeMillis()
        throughput.add(total, now)
        if (stats.connected) activeMs += sampleMs
        _session.update {
            it?.copy(
                servedBytes = total,
                rateBytes = throughput.current(),
                averageBytes = if (activeMs > 0) total * 1_000 / activeMs else 0,
                history = throughput.history,
                tvConnected = stats.connected,
                tvRequests = stats.requests,
                tvAgent = stats.agent ?: it.tvAgent,
                upstreamMs = stats.upstreamMs ?: it.upstreamMs,
                totalBytes = stats.totalBytes ?: it.totalBytes
            )
        }
    }
}
