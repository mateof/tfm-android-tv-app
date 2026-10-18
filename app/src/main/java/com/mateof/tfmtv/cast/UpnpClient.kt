package com.mateof.tfmtv.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A media renderer on the local network — usually a television. */
data class CastDevice(
    val name: String,
    /** Absolute URL of the AVTransport service's control endpoint. */
    val controlUrl: String,
    val id: String,
    /**
     * The device's own identity, which survives a new IP address or a different
     * description path. What the route decision is remembered against.
     */
    val udn: String = id,
    val model: String? = null
) {
    /** Where the renderer lives, which is also where its requests to the relay come from. */
    val host: String get() = runCatching { URI(controlUrl).host }.getOrNull().orEmpty()
}

/** What a renderer reports about the item it holds, null for what it does not know. */
data class CastPosition(val elapsedMs: Long?, val durationMs: Long?)

/**
 * Sends a video to a DLNA/UPnP renderer on the same network.
 *
 * DLNA rather than Chromecast on purpose: no Google Play services, no receiver
 * application, and it is what televisions, consoles and receivers already speak.
 * The renderer fetches the video itself, from the server or from the relay.
 *
 * Its own OkHttp, not the app's: that one rewrites every request to the
 * configured server, which would send the SOAP orders there instead of the TV.
 */
@Singleton
class UpnpClient @Inject constructor(
    private val log: CastLog
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        // webOS holds Play open for about thirty seconds before answering.
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    /**
     * Asks the network who can play video, and returns whoever answers.
     * SSDP: a datagram to the multicast group, and every renderer replies to us.
     */
    suspend fun discover(timeoutMs: Int = 3_000): List<CastDevice> = withContext(Dispatchers.IO) {
        val locations = linkedSetOf<String>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 600
                socket.broadcast = true
                val group = InetAddress.getByName(SSDP_HOST)
                // Asked for in several ways, and each one twice: not every renderer
                // answers the MediaRenderer device type, and UDP loses datagrams.
                repeat(2) {
                    for (target in SEARCH_TARGETS) {
                        val payload = searchFor(target).toByteArray()
                        socket.send(DatagramPacket(payload, payload.size, group, SSDP_PORT))
                    }
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    headerOf(String(packet.data, 0, packet.length), "LOCATION")?.let(locations::add)
                }
            }
        }.onFailure { log.w("Fallo buscando aparatos", it.message) }

        log.d("SSDP: ${locations.size} descripciones distintas")
        // ssdp:all answers with everything on the network, printers included.
        locations.mapNotNull { describe(it) }.distinctBy { it.controlUrl }
    }

    /** Reads a device description and keeps it only if it can play video. */
    suspend fun describe(location: String): CastDevice? = withContext(Dispatchers.IO) {
        runCatching {
            val xml = http.newCall(Request.Builder().url(location).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string().orEmpty()
            }
            if (!xml.contains("AVTransport")) return@withContext null
            // A description lists several services; the control URL that counts
            // is the one inside the AVTransport block, not the first in the document.
            val control = tagAfter(xml, "AVTransport", "controlURL") ?: return@withContext null
            CastDevice(
                name = tag(xml, "friendlyName") ?: URI(location).host,
                controlUrl = URI(location).resolve(control).toString(),
                id = location,
                udn = tag(xml, "UDN") ?: location,
                model = listOfNotNull(tag(xml, "manufacturer"), tag(xml, "modelName"))
                    .joinToString(" ").ifBlank { null }
            )
        }.onFailure { log.d("No se pudo leer la descripción de $location: ${it.message}") }
            .getOrNull()
    }

    /**
     * Adds a device by address, for televisions that do not answer discovery.
     * Accepts the full description URL, or just a host, and then tries the usual paths.
     */
    suspend fun describeManual(address: String): CastDevice? {
        val trimmed = address.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http", ignoreCase = true) && trimmed.endsWith(".xml")) return describe(trimmed)
        val base = if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "http://$trimmed"
        // Some usual places carry their own port; with one already typed they cannot apply.
        val hasPort = runCatching { URI(base).port > 0 }.getOrDefault(false)
        for (path in COMMON_PATHS) {
            if (hasPort && path.startsWith(":")) continue
            describe("$base$path")?.let { return it }
        }
        return null
    }

    /** Points the renderer at [source] and starts it. */
    suspend fun play(device: CastDevice, source: CastSource, title: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Several televisions refuse a new URI while the transport still
                // holds the previous one. Failing here is normal when idle.
                runCatching { soap(device, "Stop", INSTANCE, quiet = true) }

                fun setUri(metadata: String) = soap(device, "SetAVTransportURI", buildString {
                    append(INSTANCE)
                    append("<CurrentURI>").append(escape(source.url)).append("</CurrentURI>")
                    append("<CurrentURIMetaData>").append(metadata).append("</CurrentURIMetaData>")
                })

                // Metadata is where renderers are fussiest, so a refusal is
                // retried with none at all, which many of them accept.
                runCatching { setUri(escape(metadata(source, title))) }
                    .onFailure { first ->
                        log.w("${device.name} rechazó los metadatos; se reintenta sin ellos", first.message)
                        setUri("")
                    }
                soap(device, "Play", "$INSTANCE<Speed>1</Speed>")
                log.i("Enviado «$title» a ${device.name} (${source.route})", redactUrl(source.url))
            }
        }

    suspend fun stop(device: CastDevice): Result<Unit> = action(device, "Stop", INSTANCE)

    suspend fun pause(device: CastDevice): Result<Unit> = action(device, "Pause", INSTANCE)

    suspend fun resume(device: CastDevice): Result<Unit> = action(device, "Play", "$INSTANCE<Speed>1</Speed>")

    /** Absolute seek to [positionMs]; REL_TIME is the unit nearly every renderer takes. */
    suspend fun seek(device: CastDevice, positionMs: Long): Result<Unit> =
        action(device, "Seek", "$INSTANCE<Unit>REL_TIME</Unit><Target>${clock(positionMs)}</Target>")

    /** PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING…, or a failure when it no longer answers. */
    suspend fun transportState(device: CastDevice): Result<String?> = withContext(Dispatchers.IO) {
        runCatching { tag(soap(device, "GetTransportInfo", INSTANCE, quiet = true), "CurrentTransportState") }
    }

    /** Plenty of renderers answer NOT_IMPLEMENTED or all zeroes, hence the nulls. */
    suspend fun position(device: CastDevice): Result<CastPosition> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = soap(device, "GetPositionInfo", INSTANCE, quiet = true)
            CastPosition(
                elapsedMs = tag(reply, "RelTime")?.let(::parseClock),
                durationMs = tag(reply, "TrackDuration")?.let(::parseClock)?.takeIf { it > 0 }
            )
        }
    }

    private suspend fun action(device: CastDevice, action: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { soap(device, action, body); Unit } }

    private fun soap(device: CastDevice, action: String, body: String, quiet: Boolean = false): String {
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="$AV_TRANSPORT">$body</u:$action></s:Body></s:Envelope>"""

        val request = Request.Builder()
            .url(device.controlUrl)
            .addHeader("SOAPAction", "\"$AV_TRANSPORT#$action\"")
            .addHeader("Connection", "close")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()

        val started = System.currentTimeMillis()
        try {
            http.newCall(request).execute().use { response ->
                val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                val ms = System.currentTimeMillis() - started
                if (!response.isSuccessful) {
                    // A UPnP refusal carries its reason in the body; the HTTP
                    // status alone does not tell a bad format from a busy transport.
                    val error = UpnpException(
                        action = action,
                        httpCode = response.code,
                        upnpCode = tag(text, "errorCode")?.toIntOrNull(),
                        description = tag(text, "errorDescription")
                    )
                    if (!quiet) log.w("${device.name} ← $action: error en $ms ms", error.message)
                    throw error
                }
                if (!quiet) log.i("${device.name} ← $action: OK en $ms ms")
                return text
            }
        } catch (e: UpnpException) {
            throw e
        } catch (e: Exception) {
            if (!quiet) log.w("${device.name} no contestó a $action", e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    /** Minimal DIDL-Lite; renderers that ignore metadata still play the URL. */
    private fun metadata(source: CastSource, title: String): String =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
            """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="0" parentID="-1" restricted="1">""" +
            """<dc:title>${escape(title)}</dc:title>""" +
            """<upnp:class>object.item.videoItem.movie</upnp:class>""" +
            """<res protocolInfo="${Dlna.protocolInfo(source.mime, source.features)}">${escape(source.url)}</res>""" +
            """</item></DIDL-Lite>"""

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun headerOf(message: String, name: String): String? = message.lineSequence()
        .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:\\w+:)?$name>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun tagAfter(xml: String, marker: String, name: String): String? {
        val at = xml.indexOf(marker).takeIf { it >= 0 } ?: return null
        return tag(xml.substring(at), name)
    }

    private fun searchFor(target: String): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_HOST:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $target\r\n\r\n"

    companion object {
        private const val SSDP_HOST = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val INSTANCE = "<InstanceID>0</InstanceID>"

        private val COMMON_PATHS = listOf(
            "/description.xml", "/dmr.xml", "/MediaRenderer.xml",
            "/rootDesc.xml", "/upnp/desc.xml", ":8060/dial/dd.xml", ":1400/xml/device_description.xml"
        )

        private val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            AV_TRANSPORT,
            "ssdp:all"
        )

        /** "H:MM:SS", what Seek takes. */
        fun clock(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
        }

        /** "H:MM:SS" or "H:MM:SS.mmm"; null for NOT_IMPLEMENTED and the like. */
        fun parseClock(value: String): Long? {
            val parts = value.trim().split(':')
            if (parts.size != 3) return null
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].toDoubleOrNull() ?: return null
            return (h * 3600 + m * 60) * 1000 + (s * 1000).toLong()
        }
    }
}
