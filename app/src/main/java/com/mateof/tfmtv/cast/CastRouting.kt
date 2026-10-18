package com.mateof.tfmtv.cast

import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** An address range: an address plus how many leading bits name the network. */
data class Subnet(val address: InetAddress, val prefixLength: Int) {

    fun contains(other: InetAddress): Boolean {
        val mine = address.address
        val theirs = other.address
        if (mine.size != theirs.size) return false
        var bits = prefixLength.coerceIn(0, mine.size * 8)
        var i = 0
        while (bits >= 8) {
            if (mine[i] != theirs[i]) return false
            i++
            bits -= 8
        }
        if (bits == 0) return true
        val mask = (0xFF shl (8 - bits)) and 0xFF
        return (mine[i].toInt() and mask) == (theirs[i].toInt() and mask)
    }

    /** The network itself, host bits cleared: 192.168.1.37/24 → 192.168.1.0/24. */
    fun network(): Subnet {
        val bytes = address.address.copyOf()
        var bits = prefixLength.coerceIn(0, bytes.size * 8)
        for (i in bytes.indices) {
            when {
                bits >= 8 -> bits -= 8
                bits > 0 -> {
                    bytes[i] = (bytes[i].toInt() and ((0xFF shl (8 - bits)) and 0xFF)).toByte()
                    bits = 0
                }
                else -> bytes[i] = 0
            }
        }
        return Subnet(InetAddress.getByAddress(bytes), prefixLength)
    }

    override fun toString(): String = "${address.hostAddress}/$prefixLength"

    companion object {
        /** Only for literals: "10.0.0.0/8". A host name here would be a DNS lookup. */
        fun parse(cidr: String): Subnet {
            val (host, length) = cidr.split('/', limit = 2)
            return Subnet(InetAddress.getByName(host), length.toInt())
        }
    }
}

/** One entry of a network's routing table, and whether that network is a VPN. */
data class NetRoute(val destination: Subnet, val vpn: Boolean)

enum class CastRoute { DIRECT, RELAY }

data class RouteDecision(val route: CastRoute, val reason: String)

/**
 * Which way a stream should reach a television, decided before asking it
 * anything: two refused SOAP calls cost about ten seconds, and the answer is
 * usually visible from the addresses alone.
 */
object CastRouting {

    private val CGNAT = Subnet.parse("100.64.0.0/10")
    private val ULA = Subnet.parse("fc00::/7")

    /** Addresses a television on someone else's network has no way to reach. */
    fun isPrivate(address: InetAddress): Boolean =
        address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            CGNAT.contains(address) ||
            ULA.contains(address)

    /**
     * Whether this app's traffic to [host] goes into a VPN.
     *
     * Not a longest-prefix match: Android looks the VPN's own table up first
     * for an app it covers, so any VPN route that matches wins, a 0.0.0.0/0 one
     * included, even against a more specific Wi-Fi route.
     */
    fun viaVpn(routes: List<NetRoute>, host: InetAddress): Boolean =
        routes.any { it.vpn && it.destination.contains(host) }

    /**
     * @param tvSubnet the phone's network that the television is on, or null
     *   when the phone has no address there.
     * @param hostViaVpn what [viaVpn] says about the stream's host.
     */
    fun decide(host: InetAddress, tvSubnet: Subnet?, hostViaVpn: Boolean): RouteDecision {
        val where = host.hostAddress
        return when {
            tvSubnet != null && tvSubnet.contains(host) ->
                RouteDecision(CastRoute.DIRECT, "$where está en la red de la tele ($tvSubnet)")

            hostViaVpn ->
                RouteDecision(CastRoute.RELAY, "la ruta a $where pasa por la VPN")

            isPrivate(host) ->
                RouteDecision(CastRoute.RELAY, "$where es privada y no está en la red de la tele (${tvSubnet ?: "?"})")

            // A public provider: the television has its own way out to it, and
            // sending every stream through the phone would only slow it down.
            else -> RouteDecision(CastRoute.DIRECT, "$where es pública")
        }
    }

    /**
     * Whether a direct attempt failed in a way the relay can fix.
     *
     * 716 (resource not found) and a bare 500 are what a renderer answers when
     * it cannot fetch the address; 714 (illegal MIME type) when it dislikes what
     * the server answers, which the relay describes the DLNA way. A timeout is a
     * renderer still trying to reach an address it will never get to. Anything
     * else — a busy transport, an unsupported action — would fail the same way relayed.
     */
    fun shouldFallBack(error: Throwable): Boolean {
        if (generateSequence(error) { it.cause }.any { it is InterruptedIOException }) return true
        val refusal = error as? UpnpException ?: return false
        if (refusal.action !in FETCHING_ACTIONS) return false
        return refusal.upnpCode in FETCH_CODES || (refusal.upnpCode == null && refusal.httpCode == 500)
    }

    /**
     * What to put in the "split tunnel" advice: the stream host's /24 for IPv4,
     * which is what a home LAN almost always is.
     */
    fun suggestedTunnel(host: InetAddress?): String =
        if (host is Inet4Address) Subnet(host, 24).network().toString() else "192.168.0.0/24"

    /**
     * Play included on purpose: several renderers accept the address without
     * looking at it and only fail when told to start.
     */
    private val FETCHING_ACTIONS = setOf("SetAVTransportURI", "Play")
    private val FETCH_CODES = setOf(714, 716)
}

/**
 * The route that last worked for a television and a stream host, for the rest
 * of this process. The next send goes straight to it instead of failing first.
 */
class RouteMemory {
    data class Key(val deviceUdn: String, val streamHost: String)

    private val known = ConcurrentHashMap<Key, CastRoute>()

    operator fun get(key: Key): CastRoute? = known[key]

    operator fun set(key: Key, route: CastRoute) {
        known[key] = route
    }

    fun forget(key: Key) {
        known.remove(key)
    }
}
