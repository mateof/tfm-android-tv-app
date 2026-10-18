package com.mateof.tfmtv.cast

import java.io.IOException

/**
 * The relay cannot be offered from here: no address on the television's
 * network, a VPN that swallows that network too, a port already taken.
 * The message is the precise reason; [hint] is what to do about it.
 */
class RelayUnavailable(message: String, val hint: String) : IOException(message)

/** The direct route failed and the relay could not be tried either. */
class CastFailure(direct: Throwable, val relay: RelayUnavailable) :
    IOException("${direct.message} · tampoco a través de este aparato: ${relay.message}", direct)

/**
 * "Directo" was chosen in the settings and the television would not fetch the
 * video from the server. [relayWorked]: the same television took it from the
 * same server through this device earlier on.
 */
class DirectRefused(cause: Throwable, val relayWorked: Boolean) : IOException(cause.message, cause)

/** The relay stopped while the television was still being told about it. */
class RelayStopped(message: String, val hint: String?) : IOException(message)

/** Turns a failed send into something the user can act on. */
object CastHints {

    fun of(error: Throwable): String? = when (error) {
        is RelayUnavailable -> error.hint
        is CastFailure -> error.relay.hint
        is DirectRefused ->
            if (error.relayWorked) "La tele no llega al servidor, pero esta misma tele sí lo reprodujo a través de este aparato. Prueba a enviarlo así."
            else "La tele no consigue el vídeo directamente del servidor. Prueba a enviarlo a través de este aparato."
        is RelayStopped -> error.hint ?: forDetail(error.message.orEmpty())
        else -> forDetail(error.message.orEmpty())
    }

    /** A UPnP code is exact but says nothing about what to do about it. */
    private fun forDetail(detail: String): String? = when {
        detail.contains("UPnP 716") -> "La tele no encuentra el vídeo: no llega a la dirección del servidor."
        detail.contains("UPnP 714") -> "La tele no admite este formato de vídeo."
        detail.contains("UPnP 701") -> "La tele no admite esa orden en este momento."
        detail.contains("UPnP 402") || detail.contains("UPnP 401") -> "La tele no entiende la orden (no la implementa)."
        else -> null
    }

    const val NO_LAN = "Este aparato no tiene dirección en la red de la tele. Conéctalo a la misma Wi-Fi que la tele."
    const val VPN_COVERS_TV =
        "La VPN se lleva también el tráfico hacia la tele. Configúrala en túnel dividido: que sólo pase por ella la red del servidor (p. ej. %s)."
    const val PORT_BUSY = "No se pudo abrir el puerto del puente en este aparato."
    const val NO_ROUTE = "Este aparato no llega al servidor. Si usas VPN, comprueba que está conectada y que incluye la red del servidor (%s)."
    const val TV_GONE = "La tele dejó de responder: puede que se haya apagado o cambiado de red."
    const val LAN_LOST = "Este aparato perdió la red local de la tele."
}
