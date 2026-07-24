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

    fun absolute(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val base = prefs.current.normalizedBaseUrl
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else base + (if (url.startsWith("/")) url else "/$url")
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
