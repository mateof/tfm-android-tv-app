package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.ApiException
import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.data.api.SystemApi
import com.mateof.tfmtv.data.model.CapabilitiesDto
import com.mateof.tfmtv.data.prefs.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

object Feature {
    const val SEARCH = "search.v1"
    const val THUMBNAILS = "files.thumbnails"
    const val MESSAGE_CONTENT = "messages.content"
    const val MESSAGES_BROWSE = "messages.browse"
    const val REFRESH_PROGRESS = "channels.refreshProgress"
}

/**
 * What the configured server can do beyond the first /api/v1. A server that
 * does not answer the question (404) has none of it, and everything that
 * depends on a feature falls back to how the app worked before.
 */
@Singleton
class ServerFeatures @Inject constructor(
    private val api: SystemApi,
    private val prefs: ServerPreferences
) {
    private val lock = Mutex()
    private var askedTo: String? = null

    private val _capabilities = MutableStateFlow(CapabilitiesDto())
    val capabilities: StateFlow<CapabilitiesDto> = _capabilities.asStateFlow()

    /** Synchronous answer, for drawing: false until the server has been asked. */
    fun known(feature: String): Boolean = feature in _capabilities.value.features

    /** Asks the server once; an answer is remembered, a network failure is not. */
    suspend fun has(feature: String): Boolean {
        load()
        return known(feature)
    }

    suspend fun load(force: Boolean = false) = lock.withLock {
        val server = prefs.current.normalizedBaseUrl
        if (!force && askedTo == server) return@withLock
        withContext(Dispatchers.IO) { runCatching { apiCall { api.capabilities() } } }
            .onSuccess {
                _capabilities.value = it
                askedTo = server
            }
            .onFailure { e ->
                _capabilities.value = CapabilitiesDto()
                if (e is ApiException) askedTo = server
            }
    }
}
