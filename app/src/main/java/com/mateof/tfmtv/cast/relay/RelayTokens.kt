package com.mateof.tfmtv.cast.relay

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The secret in the relay's url.
 *
 * One per cast session: a new send makes a new one and the old stops working.
 * It is what keeps a stranger on the same Wi-Fi from pulling the provider's
 * stream through the phone, together with the check on the caller's address.
 * The provider's credentials never appear in the url the television sees.
 */
object RelayTokens {
    private const val BYTES = 16
    private val HEX = "0123456789abcdef".toCharArray()
    private val SHAPE = Regex("[0-9a-f]{${BYTES * 2}}")
    private const val PREFIX = "/cast/"
    private val random = SecureRandom()

    /** 128 random bits, in hex: base64 is API 26 and this app starts at 23. */
    fun issue(source: SecureRandom = random): String {
        val bytes = ByteArray(BYTES).also(source::nextBytes)
        val out = CharArray(BYTES * 2)
        bytes.forEachIndexed { i, b ->
            out[i * 2] = HEX[(b.toInt() shr 4) and 0x0F]
            out[i * 2 + 1] = HEX[b.toInt() and 0x0F]
        }
        return String(out)
    }

    fun path(token: String, extension: String): String = "$PREFIX$token$extension"

    /**
     * The token in a request path, or null when the path is not one of ours.
     * `/cast/<token>` and `/cast/<token>.ts` both count: the extension is only
     * there for renderers that judge a stream by it.
     */
    fun fromPath(path: String): String? {
        val clean = path.substringBefore('?').substringBefore('#')
        if (!clean.startsWith(PREFIX)) return null
        val token = clean.removePrefix(PREFIX).substringBefore('.')
        return token.takeIf { SHAPE.matches(it) }
    }

    /** Constant time, so the comparison does not leak how much of a guess was right. */
    fun matches(expected: String, offered: String?): Boolean =
        offered != null && MessageDigest.isEqual(expected.toByteArray(), offered.toByteArray())
}
