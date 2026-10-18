package com.mateof.tfmtv.cast.relay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import com.mateof.tfmtv.cast.NetRoute
import com.mateof.tfmtv.cast.Subnet
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/** The phone's address on one of its networks, and that network's range. */
data class LanLink(
    val network: Network,
    val address: InetAddress,
    val subnet: Subnet,
    val interfaceName: String?
)

/**
 * Reads the phone's networks as the routing decisions need them: which one
 * shares a segment with the television, and which routes belong to a VPN.
 */
@Singleton
class NetworkInspector @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /**
     * The phone's own address on the network [target] is on, if it has one.
     * VPN interfaces never count: the relay must listen where the television
     * can reach it, and a tunnel to another house is not that.
     */
    fun lanLinkFor(target: InetAddress): LanLink? {
        val manager = connectivity ?: return null
        for (network in networks()) {
            val caps = manager.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val props = manager.getLinkProperties(network) ?: continue
            for (link in props.linkAddresses) {
                val address = link.address
                if ((address is Inet4Address) != (target is Inet4Address)) continue
                val subnet = Subnet(address, link.prefixLength)
                if (subnet.contains(target)) {
                    return LanLink(network, address, subnet.network(), props.interfaceName)
                }
            }
        }
        return null
    }

    /**
     * Every IPv4 address this device has on a network that is not a VPN: where
     * other devices in the house can reach it, and where a broadcast to them
     * has to leave from.
     */
    fun lanLinks(): List<LanLink> {
        val manager = connectivity ?: return emptyList()
        return networks().flatMap { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@flatMap emptyList()
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@flatMap emptyList()
            val props = manager.getLinkProperties(network) ?: return@flatMap emptyList()
            props.linkAddresses
                .filter { it.address is Inet4Address && !it.address.isLoopbackAddress }
                .map { link ->
                    LanLink(network, link.address, Subnet(link.address, link.prefixLength), props.interfaceName)
                }
        }
    }

    /** Every unicast route of every network this app can use, VPNs marked. */
    fun routes(): List<NetRoute> {
        val manager = connectivity ?: return emptyList()
        return networks().flatMap { network ->
            val vpn = manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val props = manager.getLinkProperties(network) ?: return@flatMap emptyList()
            props.routes
                // From API 33 a VPN can exclude ranges, which show up as throw
                // routes; they mean "not through here", the opposite of a match.
                .filter { Build.VERSION.SDK_INT < 33 || it.type == RouteInfo.RTN_UNICAST }
                .map { route ->
                    NetRoute(Subnet(route.destination.address, route.destination.prefixLength), vpn)
                }
        }
    }

    // allNetworks is deprecated from API 31 in favour of callbacks, but a
    // one-off look at the routing tables is exactly what is needed here.
    @Suppress("DEPRECATION")
    private fun networks(): List<Network> = connectivity?.allNetworks?.toList().orEmpty()
}
