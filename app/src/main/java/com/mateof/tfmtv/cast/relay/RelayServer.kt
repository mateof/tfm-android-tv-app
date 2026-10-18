package com.mateof.tfmtv.cast.relay

import com.mateof.tfmtv.cast.Dlna
import com.mateof.tfmtv.core.Format
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** What the relay serves and to whom, for one send to one television. */
data class RelaySession(
    val token: String,
    /** The server's address. Carries the API key: never logged unredacted, never sent on. */
    val upstreamUrl: String,
    /** Served as Content-Type; must match the protocolInfo the renderer was given. */
    val mime: String,
    val features: String,
    /** The only address allowed to ask. */
    val client: InetAddress
)

/** What the relay has seen so far, for the transmission panel. */
data class RelayStats(
    /** Bytes the television has been given, across all its requests. */
    val servedBytes: Long,
    /** Whether the television has a connection open right now. */
    val connected: Boolean,
    /** GETs that got as far as the video: the first one, then one per seek or retry. */
    val requests: Int,
    /** How the television's player introduces itself. */
    val agent: String?,
    /** How long the server took to answer the last GET. */
    val upstreamMs: Long?,
    /** Size of the file, once the server said it. */
    val totalBytes: Long?
)

/**
 * A minimal HTTP server that hands a television the video this device can reach
 * and the television cannot — a server behind a VPN, typically.
 *
 * Written by hand on purpose, as in Kanal: DLNA renderers want plain responses
 * with the connection closed at the end, no chunked encoding, and a library
 * would be megabytes to serve one client. Ranges go through untouched, which is
 * what lets the television seek.
 *
 * Bound to this device's address on the television's network only — never the
 * wildcard, never the VPN interface — and it answers only the television's
 * address, with the session's token.
 */
class RelayServer(
    private val bindAddress: InetAddress,
    private val requestedPort: Int,
    private val http: OkHttpClient,
    private val events: Events
) {
    interface Events {
        fun info(message: String, detail: String? = null)

        /** The server could not be reached at all: not a television hanging up. */
        fun upstreamUnreachable(error: IOException)

        /** The server stopped sending halfway through a response. */
        fun upstreamCut(detail: String?) = Unit

        /** The server answered the television's request with an error status. */
        fun upstreamRefused(code: Int) = Unit

        /** A request from an address that is not the television's. */
        fun refused(address: String) = Unit
    }

    @Volatile
    var session: RelaySession? = null

    private var server: ServerSocket? = null
    private val active = AtomicInteger(0)
    private val lastActivity = AtomicLong(System.currentTimeMillis())
    private val served = AtomicLong(0)
    private val requests = AtomicInteger(0)

    @Volatile
    private var agent: String? = null

    @Volatile
    private var upstreamMs: Long? = null

    @Volatile
    private var totalBytes: Long? = null

    /** Whether the last response ended because the server stopped sending. */
    @Volatile
    private var lastCut = false

    @Volatile
    private var stopped = false
    private val sockets: MutableSet<Socket> = Collections.synchronizedSet(mutableSetOf())
    private val calls: MutableSet<Call> = Collections.synchronizedSet(mutableSetOf())

    val port: Int get() = server?.localPort ?: -1

    fun start() {
        val socket = try {
            ServerSocket(requestedPort, BACKLOG, bindAddress)
        } catch (e: IOException) {
            // The fixed port is a convenience (an emulator redirect needs one);
            // any free port works just as well for a real television.
            if (requestedPort == 0) throw e
            ServerSocket(0, BACKLOG, bindAddress)
        }
        server = socket
        lastActivity.set(System.currentTimeMillis())
        thread(name = "tfm-relay", isDaemon = true) { acceptLoop(socket) }
    }

    fun url(session: RelaySession): String {
        val host = bindAddress.hostAddress.orEmpty().substringBefore('%')
            .let { if (bindAddress is Inet6Address) "[$it]" else it }
        return "http://$host:$port" + RelayTokens.path(session.token, Dlna.extensionOf(session.mime))
    }

    fun servedBytes(): Long = served.get()

    fun connected(): Boolean = active.get() > 0

    fun stats(): RelayStats = RelayStats(served.get(), connected(), requests.get(), agent, upstreamMs, totalBytes)

    /** True when the server cut the last response and the television has not asked again since. */
    fun cutAndAbandoned(): Boolean = lastCut && !connected()

    /** How long nothing has been connected; zero while the television is reading. */
    fun idleMs(now: Long = System.currentTimeMillis()): Long =
        if (active.get() > 0) 0 else now - lastActivity.get()

    fun stop() {
        stopped = true
        session = null
        runCatching { server?.close() }
        synchronized(calls) {
            calls.forEach { it.cancel() }
            calls.clear()
        }
        synchronized(sockets) {
            sockets.forEach { runCatching { it.close() } }
            sockets.clear()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            thread(name = "tfm-relay-conn", isDaemon = true) { serve(client) }
        }
    }

    private class Exchange(
        var method: String = "?",
        var range: String? = null,
        var code: Int = 0,
        var bytes: Long = 0,
        var ending: String = ""
    )

    private class HttpRequest(val method: String, val path: String, val headers: Map<String, String>)

    private fun serve(client: Socket) {
        sockets += client
        active.incrementAndGet()
        lastActivity.set(System.currentTimeMillis())
        val started = System.nanoTime()
        val exchange = Exchange()
        try {
            if (active.get() > MAX_CONNECTIONS) {
                exchange.code = 503
                respondEmpty(client.getOutputStream(), 503)
                return
            }
            client.soTimeout = HEADER_TIMEOUT_MS
            val request = readRequest(client.getInputStream())
            if (request == null) {
                exchange.code = 400
                respondEmpty(client.getOutputStream(), 400)
                return
            }
            exchange.method = request.method
            exchange.range = request.headers["range"]
            request.headers["user-agent"]?.let { agent = it }
            handle(client, request, exchange)
        } catch (e: IOException) {
            if (exchange.ending.isEmpty()) exchange.ending = e.message ?: e.javaClass.simpleName
        } finally {
            active.decrementAndGet()
            lastActivity.set(System.currentTimeMillis())
            sockets -= client
            runCatching { client.close() }
            val ms = (System.nanoTime() - started) / 1_000_000
            if (exchange.code != 0) {
                events.info(
                    "Tele → ${exchange.method}" + (exchange.range?.let { " $it" } ?: "") +
                        " · ${exchange.code} · ${Format.bytes(exchange.bytes).ifBlank { "0 B" }} · ${ms / 1000} s",
                    exchange.ending.ifEmpty { null }
                )
            }
        }
    }

    private fun handle(client: Socket, request: HttpRequest, exchange: Exchange) {
        val out = client.getOutputStream()
        val current = session
        if (current == null || client.inetAddress != current.client) {
            exchange.code = 403
            exchange.ending = "petición de ${client.inetAddress.hostAddress}, no de la tele"
            respondEmpty(out, 403)
            if (current != null) events.refused(client.inetAddress.hostAddress.orEmpty())
            return
        }
        if (request.method != "GET" && request.method != "HEAD") {
            exchange.code = 405
            respondEmpty(out, 405)
            return
        }
        if (!RelayTokens.matches(current.token, RelayTokens.fromPath(request.path))) {
            exchange.code = 404
            exchange.ending = "token no válido"
            respondEmpty(out, 404)
            return
        }
        if (request.method == "HEAD") head(out, current, exchange) else proxy(client, out, request, current, exchange)
    }

    /**
     * Answered with the real size, so the television knows the file can be
     * seeked. Asked as a one-byte range rather than a HEAD: not every server
     * answers HEAD on a stream, and a range always says the total.
     */
    private fun head(out: OutputStream, current: RelaySession, exchange: Exchange) {
        val headers = dlnaHeaders(current).toMutableList()
        val total = totalBytes ?: probeSize(current)
        if (total != null) {
            headers += "Content-Length" to total.toString()
            headers += "Accept-Ranges" to "bytes"
        }
        exchange.code = 200
        writeHead(out, 200, headers)
    }

    private fun probeSize(current: RelaySession): Long? {
        val call = http.newCall(
            Request.Builder().url(current.upstreamUrl).header("Range", "bytes=0-0").build()
        )
        return runCatching {
            call.execute().use { reply ->
                reply.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: reply.header("Content-Length")?.toLongOrNull()?.takeIf { reply.code == 200 }
            }
        }.getOrNull()?.also { totalBytes = it }
    }

    private fun proxy(
        client: Socket,
        out: OutputStream,
        request: HttpRequest,
        current: RelaySession,
        exchange: Exchange
    ) {
        val upstream = Request.Builder()
            .url(current.upstreamUrl)
            .apply { request.headers["range"]?.let { header("Range", it) } }
            .build()
        val call = http.newCall(upstream)
        calls += call

        try {
            val asked = System.nanoTime()
            val response = try {
                call.execute()
            } catch (e: IOException) {
                exchange.code = 502
                exchange.ending = "este aparato no llega al servidor: ${e.javaClass.simpleName}"
                respondEmpty(out, 502)
                // After answering: being told stops this very server.
                if (!call.isCanceled()) events.upstreamUnreachable(e)
                return
            }

            response.use { reply ->
                if (!reply.isSuccessful) {
                    exchange.code = if (reply.code in 400..499) reply.code else 502
                    exchange.ending = "el servidor respondió ${reply.code}"
                    respondEmpty(out, exchange.code)
                    events.upstreamRefused(reply.code)
                    return
                }
                val piece = ContentRange.parse(reply.header("Content-Range"))
                piece?.let { totalBytes = it.total }
                if (reply.code == 200) reply.header("Content-Length")?.toLongOrNull()?.let { totalBytes = it }
                val wanted = ByteRange.parse(request.headers["range"])
                val stitchTo = stitchEnd(reply.code, wanted, piece)

                val headers = dlnaHeaders(current).toMutableList()
                val status: Int
                if (stitchTo != null && piece != null) {
                    // The server caps every answer at a few MB however much was
                    // asked for. ExoPlayer asks again; ffmpeg-based renderers
                    // (AirScreen on Fire TV) take the short body for the end of
                    // the file and stop. So the television gets one response up
                    // to where it asked, and the pieces are fetched here.
                    headers += "Content-Length" to (stitchTo - piece.start + 1).toString()
                    headers += "Accept-Ranges" to "bytes"
                    status = if (wanted == null) {
                        200
                    } else {
                        headers += "Content-Range" to "bytes ${piece.start}-$stitchTo/${piece.total}"
                        206
                    }
                } else {
                    reply.header("Content-Length")?.let { headers += "Content-Length" to it }
                    reply.header("Content-Range")?.let { headers += "Content-Range" to it }
                    if (reply.code == 206 || reply.header("Accept-Ranges") == "bytes") headers += "Accept-Ranges" to "bytes"
                    status = if (reply.code == 206) 206 else 200
                }
                exchange.code = status
                writeHead(out, status, headers)

                val body = reply.body ?: return
                client.soTimeout = 0
                upstreamMs = (System.nanoTime() - asked) / 1_000_000
                lastCut = false
                requests.incrementAndGet()
                if (!copy(body.byteStream(), out, exchange, call)) return
                if (stitchTo != null && piece != null) stitch(current, out, exchange, piece.end + 1, stitchTo)
            }
        } finally {
            calls -= call
        }
    }

    /**
     * Where the television's response has to end when the server answered a
     * shorter piece than asked for, or null when the answer can go as it is.
     */
    private fun stitchEnd(code: Int, wanted: ByteRange?, piece: ContentRange?): Long? {
        if (code != 206 || piece == null) return null
        if (piece.start != (wanted?.start ?: 0L)) return null
        val end = minOf(wanted?.end ?: (piece.total - 1), piece.total - 1)
        return end.takeIf { it > piece.end }
    }

    /** Fetches and forwards the pieces from [from] to [to], inclusive, one after another. */
    private fun stitch(current: RelaySession, out: OutputStream, exchange: Exchange, from: Long, to: Long) {
        var next = from
        var pieces = 1
        while (next <= to && !stopped) {
            val call = http.newCall(
                Request.Builder().url(current.upstreamUrl).header("Range", "bytes=$next-$to").build()
            )
            calls += call
            try {
                val reply = try {
                    call.execute()
                } catch (e: IOException) {
                    exchange.ending = "el servidor no contestó al trozo ${pieces + 1}: ${e.message}"
                    if (!call.isCanceled()) {
                        lastCut = true
                        events.upstreamCut(e.message)
                    }
                    return
                }
                reply.use {
                    val piece = ContentRange.parse(it.header("Content-Range"))
                    if (it.code != 206 || piece == null || piece.start != next) {
                        // Anything else would put the wrong bytes in the middle of the film.
                        exchange.ending = "el servidor contestó ${it.code} al trozo desde $next"
                        events.upstreamRefused(it.code)
                        return
                    }
                    val body = it.body ?: return
                    pieces++
                    if (!copy(body.byteStream(), out, exchange, call)) return
                    next = piece.end + 1
                }
            } finally {
                calls -= call
            }
        }
        exchange.ending = "completa en $pieces trozos"
    }

    /**
     * Reads and writes are told apart because they mean different things: a
     * write failing is the television hanging up — routine, it does it on every
     * seek — while a read failing is the server cutting the response.
     *
     * @return true when the server's body ended normally.
     */
    private fun copy(input: InputStream, out: OutputStream, exchange: Exchange, call: Call): Boolean {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                exchange.ending = when {
                    stopped -> "puente cerrado"
                    call.isCanceled() -> "cancelada"
                    else -> "el servidor cortó: ${e.message}"
                }
                if (!stopped && !call.isCanceled()) {
                    lastCut = true
                    events.upstreamCut(e.message)
                }
                return false
            }
            if (read < 0) {
                exchange.ending = "completa"
                return true
            }
            try {
                out.write(buffer, 0, read)
            } catch (_: IOException) {
                exchange.ending = if (stopped) "puente cerrado" else "la tele cerró la conexión"
                call.cancel()
                return false
            }
            exchange.bytes += read
            served.addAndGet(read.toLong())
            lastActivity.set(System.currentTimeMillis())
        }
    }

    private fun dlnaHeaders(current: RelaySession): List<Pair<String, String>> = listOf(
        "Content-Type" to current.mime,
        "transferMode.dlna.org" to "Streaming",
        "contentFeatures.dlna.org" to current.features,
        "Cache-Control" to "no-cache",
        "Server" to SERVER_NAME
    )

    private fun writeHead(out: OutputStream, status: Int, headers: List<Pair<String, String>>) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
            headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun respondEmpty(out: OutputStream, status: Int) {
        runCatching { writeHead(out, status, listOf("Content-Length" to "0", "Server" to SERVER_NAME)) }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val raw = StringBuilder()
        while (true) {
            if (raw.length >= MAX_HEADER_BYTES) return null
            val b = input.read()
            if (b < 0) return null
            raw.append(b.toChar())
            if (b == '\n'.code && (raw.endsWith("\r\n\r\n") || raw.endsWith("\n\n"))) break
        }
        val lines = raw.lines().map { it.trimEnd('\r') }
        val parts = lines.firstOrNull()?.split(' ')?.takeIf { it.size >= 2 } ?: return null
        val headers = lines.drop(1)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim().lowercase(Locale.ROOT) to it.substringAfter(':').trim() }
        return HttpRequest(parts[0].uppercase(Locale.ROOT), parts[1], headers)
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        416 -> "Range Not Satisfiable"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Status"
    }

    private companion object {
        const val BACKLOG = 8
        const val MAX_CONNECTIONS = 8
        const val HEADER_TIMEOUT_MS = 10_000
        const val MAX_HEADER_BYTES = 16 * 1024
        const val BUFFER_BYTES = 64 * 1024
        const val SERVER_NAME = "TFMTV/relay UPnP/1.0 DLNADOC/1.50"
    }
}

/** A request's `Range: bytes=start-[end]`; suffix and multi ranges are left alone. */
internal data class ByteRange(val start: Long, val end: Long?) {
    companion object {
        private val SHAPE = Regex("bytes=(\\d+)-(\\d*)")

        fun parse(header: String?): ByteRange? {
            val match = header?.trim()?.let(SHAPE::matchEntire) ?: return null
            return ByteRange(match.groupValues[1].toLong(), match.groupValues[2].toLongOrNull())
        }
    }
}

/** A response's `Content-Range: bytes start-end/total`. */
internal data class ContentRange(val start: Long, val end: Long, val total: Long) {
    companion object {
        private val SHAPE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

        fun parse(header: String?): ContentRange? {
            val match = header?.trim()?.let(SHAPE::matchEntire) ?: return null
            val (start, end, total) = match.destructured
            return ContentRange(start.toLong(), end.toLong(), total.toLong())
        }
    }
}
