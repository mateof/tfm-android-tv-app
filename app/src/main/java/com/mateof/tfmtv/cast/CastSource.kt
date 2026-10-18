package com.mateof.tfmtv.cast

import java.io.IOException

/**
 * What a renderer is handed: the address it will fetch and how that resource
 * is described to it.
 *
 * The description has to agree with what is actually served. Picky renderers
 * (LG and Samsung among them) compare the protocolInfo in the metadata with the
 * Content-Type and contentFeatures they get back, and refuse a mismatch.
 */
data class CastSource(
    val url: String,
    val mime: String,
    /** Fourth field of protocolInfo; the relay also sends it as contentFeatures.dlna.org. */
    val features: String,
    /** How it got here, for the panel: [ROUTE_DIRECT], [ROUTE_RELAY] or [ROUTE_FALLBACK]. */
    val route: String
) {
    companion object {
        const val ROUTE_DIRECT = "directo"
        const val ROUTE_RELAY = "relay"
        const val ROUTE_FALLBACK = "fallback→relay"
    }
}

object Dlna {
    /**
     * A file: byte-range seeking (OP=01), not transcoded (CI=0), streaming
     * transfer mode, background mode, connection stall and DLNA 1.5 (FLAGS=0170…).
     */
    const val FILE_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    /** Renderers match on this; announcing the wrong container gets refused. */
    fun mimeOf(fileName: String): String {
        val name = fileName.substringBefore('?').lowercase()
        return when (name.substringAfterLast('.', "")) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "mpg", "mpeg" -> "video/mpeg"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    /** The extension the relay puts on its url, for renderers that judge by it. */
    fun extensionOf(mime: String): String = when (mime) {
        "video/mp4" -> ".mp4"
        "video/x-matroska" -> ".mkv"
        "video/webm" -> ".webm"
        "video/x-msvideo" -> ".avi"
        "video/quicktime" -> ".mov"
        "video/x-ms-wmv" -> ".wmv"
        "video/x-flv" -> ".flv"
        "video/mp2t" -> ".ts"
        "video/mpeg" -> ".mpg"
        "video/3gpp" -> ".3gp"
        else -> ""
    }

    fun protocolInfo(mime: String, features: String): String = "http-get:*:$mime:$features"
}

/**
 * A renderer's refusal, kept typed so the decision to retry through the relay
 * can look at the code instead of parsing a sentence.
 */
class UpnpException(
    val action: String,
    val httpCode: Int,
    val upnpCode: Int?,
    val description: String?
) : IOException(
    buildString {
        append("$action devolvió $httpCode")
        if (upnpCode != null) append(" · UPnP $upnpCode")
        if (description != null) append(" · $description")
    }
)
