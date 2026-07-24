package com.mateof.tfmtv.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.api.SystemApi
import com.mateof.tfmtv.data.prefs.ServerPreferences
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val testing: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val system: SystemApi
) : ViewModel() {

    private val _state = MutableStateFlow(
        SetupState(baseUrl = prefs.current.baseUrl, apiKey = prefs.current.apiKey)
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = prefs.awaitLoaded()
            _state.update { it.copy(baseUrl = config.baseUrl, apiKey = config.apiKey) }
        }
    }

    fun setUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun setKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }

    fun connect() {
        val url = normalize(_state.value.baseUrl)
        if (url.isBlank()) {
            _state.update { it.copy(error = "Introduce la dirección del servidor") }
            return
        }
        _state.update { it.copy(testing = true, error = null, baseUrl = url) }
        viewModelScope.launch {
            prefs.save(url, _state.value.apiKey.trim())
            runCatching { system.info() }
                .onSuccess { _state.update { s -> s.copy(testing = false, saved = true) } }
                .onFailure { e ->
                    _state.update { s -> s.copy(testing = false, error = e.userMessage()) }
                }
        }
    }

    /** Typing a scheme on a D-pad keyboard is painful, so plain host:port is accepted. */
    private fun normalize(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank()) return ""
        return if (value.startsWith("http://") || value.startsWith("https://")) value
        else "http://$value"
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val vm: SetupViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val firstField = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(640.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("TFM TV", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Conecta con tu servidor de Telegram File Manager.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::setUrl,
                label = { androidx.compose.material3.Text("Servidor (ej. 192.168.1.10:5257)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstField)
                    .dpadEscape(focusManager)
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = vm::setKey,
                label = { androidx.compose.material3.Text("API key (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().dpadEscape(focusManager)
            )

            if (state.error != null) {
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = vm::connect,
                enabled = !state.testing,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.testing) "Conectando…" else "Conectar")
            }
        }
    }
}

/** A focused text field eats the D-pad up/down keys, so focus has to be moved by hand. */
private fun Modifier.dpadEscape(focusManager: FocusManager): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
