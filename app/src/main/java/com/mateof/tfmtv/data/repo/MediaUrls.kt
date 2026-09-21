package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.data.prefs.ServerPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds absolute, playable URLs from the (sometimes relative) URLs the API
 * returns, appending the API key for consumers that cannot send headers
 * (media players, MediaMetadataRetriever, image loaders).
 */
@Singleton
class MediaUrls @Inject constructor(private val prefs: ServerPreferences) {

    /**
     * Rebuilds the URL on the server the user configured. The API returns
     * absolute URLs built from the host it sees, which behind a reverse proxy
     * is its internal address and certificate: playing those from outside the
     * LAN fails (the API calls themselves don't, because the OkHttp
     * interceptor already rewrites their host). Only the path is kept.
     */
    fun absolute(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val base = prefs.current.normalizedBaseUrl
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))
            return base + (if (url.startsWith("/")) url else "/$url")
        val afterScheme = url.indexOf("//") + 2
        val pathStart = url.indexOf('/', afterScheme)
        return if (pathStart < 0) base else base + url.substring(pathStart)
    }

    /** Absolute URL with the API key appended as query parameter. */
    fun withKey(url: String?): String? {
        val abs = absolute(url) ?: return null
        val key = prefs.current.apiKey
        if (key.isBlank()) return abs
        val sep = if (abs.contains('?')) '&' else '?'
        return "$abs${sep}apiKey=$key"
    }

    fun channelImage(channelId: Long): String? =
        withKey("/api/v1/channels/$channelId/image")
}
