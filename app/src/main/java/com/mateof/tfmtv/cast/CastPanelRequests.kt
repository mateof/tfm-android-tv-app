package com.mateof.tfmtv.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks the navigation to open the transmission panel, from the notification.
 * A flag rather than an event: the activity may be created before the
 * navigation is there to hear it.
 */
object CastPanelRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
