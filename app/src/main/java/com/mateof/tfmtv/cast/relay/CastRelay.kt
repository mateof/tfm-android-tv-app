package com.mateof.tfmtv.cast.relay

import android.content.Context
import com.mateof.tfmtv.cast.CastDevice
import com.mateof.tfmtv.cast.CastHints
import com.mateof.tfmtv.cast.CastLog
import com.mateof.tfmtv.cast.CastRouting
import com.mateof.tfmtv.cast.RelayUnavailable
import com.mateof.tfmtv.cast.redactUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RelayStatus {
    data object Idle : RelayStatus

    data class Running(
        val device: CastDevice,
        val title: String,
        val link: LanLink
    ) : RelayStatus
}

/**
 * A relay that stopped on its own rather than because a new send replaced it.
 * [problem] is null for an ordinary end and set when the user should hear why.
 */
data class RelayEnded(
    val device: CastDevice,
    val problem: String?,
    val hint: String?,
    /** The session it ended; see [CastRelay.generation]. */
    val generation: Int = 0,
    /** Ended by something on the server's side that sending again may well get past. */
    val recoverable: Boolean = false,
    /** The user asked for it. */
    val byUser: Boolean = false
)

/**
 * Owns the relay server and the one session it serves.
 *
 * The server starts here rather than in [CastRelayService] because its address
 * has to be known before the television is told about it; the service is what
 * keeps the process alive while it runs.
 */
@Singleton
class CastRelay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: NetworkInspector,
    private val log: CastLog
) {
    private val _status = MutableStateFlow<RelayStatus>(RelayStatus.Idle)
    val status: StateFlow<RelayStatus> = _status.asStateFlow()

    private val _ended = MutableSharedFlow<RelayEnded>(extraBufferCapacity = 4)
    val ended: SharedFlow<RelayEnded> = _ended.asSharedFlow()

    @Volatile
    var lastEnded: RelayEnded? = null
        private set

    private var server: RelayServer? = null

    /**
     * Its own client: the app's one rewrites every request to the configured
     * server, which is right here but would hide a mistake, and logs nothing
     * useful. A short connect timeout, because a television gives up on a
     * silent address after about fifteen seconds and the server being out of
     * reach has to be known before that.
     */
    private val upstream = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Counts sessions, so a service that served an old one cannot close the new one. */
    @Volatile
    var generation = 0
        private set

    /**
     * Starts serving [upstreamUrl] to [tv] alone and returns the address to give it.
     *
     * @throws RelayUnavailable when this device cannot be reached from the television's network.
     */
    @Synchronized
    fun open(
        device: CastDevice,
        tv: InetAddress,
        upstreamHost: InetAddress?,
        upstreamUrl: String,
        mime: String,
        features: String,
        title: String
    ): String {
        server?.stop()
        server = null
        val tunnel = CastRouting.suggestedTunnel(upstreamHost)
        val link: LanLink
        val relay: RelayServer
        try {
            link = network.lanLinkFor(tv) ?: throw RelayUnavailable(
                "este aparato no tiene dirección en la red de la tele (${tv.hostAddress})",
                CastHints.NO_LAN
            )
            if (CastRouting.viaVpn(network.routes(), tv)) {
                // The relay would listen on the right interface, but the answers
                // to the television would go into the tunnel and never reach it.
                throw RelayUnavailable(
                    "la VPN también se lleva el tráfico hacia la tele (${link.subnet})",
                    CastHints.VPN_COVERS_TV.format(tunnel)
                )
            }
            relay = RelayServer(link.address, PORT, upstream, events(tunnel))
            try {
                relay.start()
            } catch (e: IOException) {
                throw RelayUnavailable(
                    "no se pudo abrir un puerto en ${link.address.hostAddress}: ${e.message}",
                    CastHints.PORT_BUSY
                )
            }
        } catch (e: RelayUnavailable) {
            shutdown()
            throw e
        }
        val session = RelaySession(
            token = RelayTokens.issue(),
            upstreamUrl = upstreamUrl,
            mime = mime,
            features = features,
            client = tv
        )
        relay.session = session
        server = relay
        val url = relay.url(session)
        log.i(
            "Puente abierto en ${link.address.hostAddress}:${relay.port} (${link.interfaceName ?: "?"}, ${link.subnet})",
            "sólo para ${tv.hostAddress}; origen ${redactUrl(upstreamUrl)} · $mime"
        )
        generation++
        lastEnded = null
        _status.value = RelayStatus.Running(device, title, link)
        CastRelayService.start(context)
        return url
    }

    fun idleMs(): Long? = server?.idleMs()

    fun stats(): RelayStats? = server?.stats()

    fun port(): Int? = server?.port?.takeIf { it > 0 }

    fun running(generation: Int): Boolean = server != null && generation == this.generation

    fun cutAndAbandoned(): Boolean = server?.cutAndAbandoned() ?: false

    /**
     * Whether the television is pulling the video right now, with at least
     * [minBytes] already through: proof of playback that does not depend on
     * how the renderer words its answers.
     */
    fun tvReading(minBytes: Long = 1): Boolean =
        server?.let { it.connected() && it.servedBytes() >= minBytes } ?: false

    @Synchronized
    fun closeIfStill(generation: Int, reason: String) {
        if (generation == this.generation) close(reason)
    }

    /** Stopped by the app: a new send, or the user stopping it. */
    @Synchronized
    fun close(reason: String) {
        if (server == null) return
        log.i("Puente cerrado: $reason")
        lastEnded = null
        shutdown()
    }

    /** Stopped by itself; the screens and the notification are told. */
    @Synchronized
    fun end(problem: String?, hint: String? = null, recoverable: Boolean = false, byUser: Boolean = false) {
        val running = _status.value as? RelayStatus.Running ?: return
        if (problem != null) log.w("Puente detenido", problem) else log.i("Puente terminado")
        val ended = RelayEnded(running.device, problem, hint, generation, recoverable, byUser)
        lastEnded = ended
        shutdown()
        _ended.tryEmit(ended)
    }

    @Synchronized
    fun endIfStill(generation: Int, problem: String?, hint: String? = null) {
        if (generation == this.generation) end(problem, hint)
    }

    private fun shutdown() {
        server?.stop()
        server = null
        _status.value = RelayStatus.Idle
    }

    private fun events(tunnel: String) = object : RelayServer.Events {
        override fun info(message: String, detail: String?) = log.i(message, detail)

        override fun upstreamUnreachable(error: IOException) {
            end(
                "este aparato no llega al servidor (${error.javaClass.simpleName}: ${error.message})",
                CastHints.NO_ROUTE.format(tunnel),
                // A VPN that is reconnecting looks exactly like this.
                recoverable = true
            )
        }

        override fun upstreamCut(detail: String?) = log.w("El servidor cortó el envío a mitad", detail)

        override fun upstreamRefused(code: Int) = log.w("El servidor rechazó la petición de la tele ($code)")

        override fun refused(address: String) = log.w("Petición rechazada de $address: no es la tele")
    }

    private companion object {
        /** Fixed so an emulator can redirect it; any free one is taken when busy. */
        const val PORT = 47830
    }
}
