package com.mateof.tfmtv.cast.relay

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/**
 * The relay against an upstream that behaves like TFM's GetFileStreamCached:
 * every answer is capped at [PIECE] bytes however much was asked for.
 */
class RelayServerTest {

    private val file = ByteArray(SIZE) { (it * 31 + it / 7).toByte() }
    private val loopback = InetAddress.getByName("127.0.0.1")
    private lateinit var upstream: ServerSocket
    private lateinit var relay: RelayServer
    private lateinit var url: String

    @Before
    fun setUp() {
        upstream = ServerSocket(0, 8, loopback)
        thread(isDaemon = true) {
            while (!upstream.isClosed) {
                val socket = runCatching { upstream.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { answer(socket) }
            }
        }
        relay = RelayServer(loopback, 0, OkHttpClient(), object : RelayServer.Events {
            override fun info(message: String, detail: String?) = Unit
            override fun upstreamUnreachable(error: IOException) = Unit
        })
        relay.start()
        val session = RelaySession(
            token = RelayTokens.issue(),
            upstreamUrl = "http://127.0.0.1:${upstream.localPort}/video",
            mime = "video/mp4",
            features = "DLNA.ORG_OP=01",
            client = loopback
        )
        relay.session = session
        url = relay.url(session)
    }

    @After
    fun tearDown() {
        relay.stop()
        upstream.close()
    }

    @Test
    fun openRangeIsServedWholeAcrossPieces() {
        val (code, headers, body) = get("bytes=0-")
        assertEquals(206, code)
        assertEquals("bytes 0-${SIZE - 1}/$SIZE", headers["Content-Range"])
        assertEquals(SIZE.toString(), headers["Content-Length"])
        assertArrayEquals(file, body)
    }

    @Test
    fun noRangeGetsTheWholeFileAs200() {
        val (code, headers, body) = get(null)
        assertEquals(200, code)
        assertEquals(SIZE.toString(), headers["Content-Length"])
        assertArrayEquals(file, body)
    }

    @Test
    fun seekFromTheMiddleRunsToTheEnd() {
        val from = 2_500_001
        val (code, headers, body) = get("bytes=$from-")
        assertEquals(206, code)
        assertEquals("bytes $from-${SIZE - 1}/$SIZE", headers["Content-Range"])
        assertArrayEquals(file.copyOfRange(from, SIZE), body)
    }

    @Test
    fun closedRangeLongerThanAPieceIsCompleted() {
        val (code, headers, body) = get("bytes=100-2500000")
        assertEquals(206, code)
        assertEquals("bytes 100-2500000/$SIZE", headers["Content-Range"])
        assertArrayEquals(file.copyOfRange(100, 2_500_001), body)
    }

    @Test
    fun rangeInsideOnePieceGoesThroughAsIs() {
        val (code, headers, body) = get("bytes=10-19")
        assertEquals(206, code)
        assertEquals("bytes 10-19/$SIZE", headers["Content-Range"])
        assertArrayEquals(file.copyOfRange(10, 20), body)
    }

    /** One GET, answered with at most [PIECE] bytes of the requested range. */
    private fun answer(socket: Socket) = socket.use {
        val reader = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val lines = generateSequence { reader.readLine() }.takeWhile { line -> line.isNotEmpty() }.toList()
        val rangeHeader = lines.firstOrNull { line -> line.startsWith("Range:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        val range = ByteRange.parse(rangeHeader)
        val start = range?.start ?: 0L
        val end = minOf(range?.end ?: (SIZE - 1L), SIZE - 1L, start + PIECE - 1)
        val head = "HTTP/1.1 206 Partial Content\r\n" +
            "Content-Type: video/mp4\r\n" +
            "Content-Range: bytes $start-$end/$SIZE\r\n" +
            "Content-Length: ${end - start + 1}\r\n" +
            "Connection: close\r\n\r\n"
        val out = it.getOutputStream()
        runCatching {
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(file, start.toInt(), (end - start + 1).toInt())
            out.flush()
        }
    }

    private fun get(range: String?): Triple<Int, Map<String, String>, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        range?.let { connection.setRequestProperty("Range", it) }
        val code = connection.responseCode
        val headers = listOf("Content-Range", "Content-Length")
            .mapNotNull { name -> connection.getHeaderField(name)?.let { name to it } }
            .toMap()
        val body = connection.inputStream.use { it.readBytes() }
        return Triple(code, headers, body)
    }

    private companion object {
        const val SIZE = 3_000_000
        const val PIECE = 1_000_000L
    }
}
