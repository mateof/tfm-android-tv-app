package com.mateof.tfmtv.cast

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class CastEvent(
    val at: Long,
    val text: String,
    /** The literal detail — what the server or the television said — for the second line. */
    val detail: String? = null,
    val level: Level = Level.INFO
) {
    enum class Level { INFO, WARN, ERROR }
}

/**
 * The conversation with the television, for the transmission panel: every SOAP
 * order and its answer, every request the television makes to the relay, and
 * every decision taken on the way. Also goes to logcat.
 */
@Singleton
class CastLog @Inject constructor() {

    private val _events = MutableStateFlow<List<CastEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<CastEvent>> = _events.asStateFlow()

    fun i(text: String, detail: String? = null) = add(text, detail, CastEvent.Level.INFO)

    fun w(text: String, detail: String? = null) = add(text, detail, CastEvent.Level.WARN)

    fun e(text: String, detail: String? = null) = add(text, detail, CastEvent.Level.ERROR)

    /** Only to logcat: too chatty for the panel. */
    fun d(text: String) {
        Log.d(TAG, text)
    }

    fun clear() {
        _events.value = emptyList()
    }

    private fun add(text: String, detail: String?, level: CastEvent.Level) {
        val line = if (detail != null) "$text — $detail" else text
        when (level) {
            CastEvent.Level.INFO -> Log.i(TAG, line)
            CastEvent.Level.WARN -> Log.w(TAG, line)
            CastEvent.Level.ERROR -> Log.e(TAG, line)
        }
        val event = CastEvent(System.currentTimeMillis(), text, detail, level)
        _events.update { (listOf(event) + it).take(MAX_EVENTS) }
    }

    private companion object {
        const val TAG = "Cast"
        const val MAX_EVENTS = 300
    }
}

/** The API key travels in the query string; it never goes into the log. */
fun redactUrl(url: String): String =
    url.replace(Regex("(?i)(apiKey|api_key|key|token)=[^&]*"), "$1=***")
