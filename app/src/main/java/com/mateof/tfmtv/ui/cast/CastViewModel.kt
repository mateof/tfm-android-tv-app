package com.mateof.tfmtv.ui.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.cast.CastController
import com.mateof.tfmtv.cast.CastDevice
import com.mateof.tfmtv.cast.CastHints
import com.mateof.tfmtv.cast.CastRequest
import com.mateof.tfmtv.cast.DirectRefused
import com.mateof.tfmtv.cast.UpnpClient
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.prefs.CastRouteMode
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.MediaUrls
import com.mateof.tfmtv.data.repo.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A video offered to the network. [startMs] null asks the server where it was left. */
data class CastTarget(
    val file: ApiFileDto,
    val channelId: Long,
    val title: String,
    val startMs: Long? = null
)

data class CastUiState(
    val target: CastTarget? = null,
    val devices: List<CastDevice> = emptyList(),
    val searching: Boolean = false,
    val sending: CastDevice? = null,
    val sentTo: String? = null,
    /** Whether the video goes through this device rather than straight from the server. */
    val viaPhone: Boolean = false,
    val error: String? = null,
    val hint: String? = null,
    /** Directo failed in a way the relay gets past: offer it. */
    val canTryRelay: Boolean = false
) {
    val open: Boolean get() = target != null
}

/** The "send to the TV" sheet: finds renderers and hands one the video. */
@HiltViewModel
class CastViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val upnp: UpnpClient,
    private val cast: CastController,
    private val mediaUrls: MediaUrls,
    private val watch: WatchRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    /** Devices typed in by hand; they never come back from a scan. */
    private var manual: List<CastDevice> = emptyList()

    private var failedDevice: CastDevice? = null

    fun open(target: CastTarget) {
        _state.value = CastUiState(target = target, devices = _state.value.devices)
        viewModelScope.launch {
            loadRemembered()
            search()
        }
    }

    fun close() {
        _state.update { it.copy(target = null, error = null, hint = null, sentTo = null, canTryRelay = false) }
    }

    fun search() {
        if (_state.value.searching) return
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null, hint = null) }
            val found = upnp.discover()
            _state.update {
                it.copy(
                    devices = merge(found),
                    searching = false,
                    hint = if (found.isEmpty() && manual.isEmpty()) NOTHING_FOUND else null
                )
            }
        }
    }

    fun addByAddress(address: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null, hint = null) }
            val device = upnp.describeManual(address)
            if (device == null) {
                _state.update {
                    it.copy(
                        searching = false,
                        error = address.trim(),
                        hint = "Nadie contesta como reproductor DLNA en esa dirección. Prueba con la IP de la tele, o con la URL completa de su descripción (…/description.xml)."
                    )
                }
            } else {
                manual = (manual + device).distinctBy { it.controlUrl }
                prefs.rememberCastAddress(address.trim())
                _state.update { it.copy(searching = false, devices = merge(it.devices)) }
            }
        }
    }

    /** After Directo failed: the same video, to the same television, through this device. */
    fun sendViaPhone() {
        failedDevice?.let { sendTo(it, CastRouteMode.RELAY) }
    }

    fun sendTo(device: CastDevice, route: CastRouteMode? = null) {
        val target = _state.value.target ?: return
        if (_state.value.sending != null) return
        viewModelScope.launch {
            _state.update { it.copy(sending = device, error = null, hint = null, canTryRelay = false, sentTo = null) }
            val url = mediaUrls.withKey(target.file.streamUrl ?: target.file.downloadUrl)
            if (url == null) {
                _state.update { it.copy(sending = null, error = "", hint = "Este vídeo no tiene URL de reproducción.") }
                return@launch
            }
            val start = target.startMs ?: watch.resumePosition(target.channelId, target.file.id)
            val request = CastRequest(
                title = target.title,
                url = url,
                fileName = target.file.name,
                channelId = target.channelId,
                fileId = target.file.id,
                startMs = start,
                route = route
            )
            cast.send(device, request)
                .onSuccess { outcome ->
                    _state.update { it.copy(sending = null, sentTo = device.name, viaPhone = outcome.viaPhone) }
                }
                .onFailure { failure ->
                    failedDevice = device
                    _state.update {
                        it.copy(
                            sending = null,
                            error = failure.message ?: failure.javaClass.simpleName,
                            hint = CastHints.of(failure),
                            canTryRelay = failure is DirectRefused
                        )
                    }
                }
        }
    }

    private suspend fun loadRemembered() {
        if (manual.isNotEmpty()) return
        val found = prefs.castAddresses.first().mapNotNull { upnp.describeManual(it) }
        if (found.isEmpty()) return
        manual = found
        _state.update { it.copy(devices = merge(it.devices)) }
    }

    /** Discovery must never drop what the user added by hand. */
    private fun merge(discovered: List<CastDevice>): List<CastDevice> =
        (discovered + manual).distinctBy { it.controlUrl }

    private companion object {
        const val NOTHING_FOUND =
            "No ha aparecido ninguna tele. Comprueba que está encendida y en la misma red, o añádela por su IP."
    }
}
