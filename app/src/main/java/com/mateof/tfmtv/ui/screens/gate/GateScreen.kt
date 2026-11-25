package com.mateof.tfmtv.ui.screens.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.api.SystemApi
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.Logo
import com.mateof.tfmtv.ui.components.PillButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GateState(
    val checking: Boolean = true,
    val needsSetup: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GateViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val system: SystemApi
) : ViewModel() {

    private val _state = MutableStateFlow(GateState())
    val state: StateFlow<GateState> = _state.asStateFlow()

    init { check() }

    fun check() {
        _state.update { GateState(checking = true) }
        viewModelScope.launch {
            val config = prefs.awaitLoaded()
            if (!config.configured || config.baseUrl.isBlank()) {
                _state.update { GateState(checking = false, needsSetup = true) }
                return@launch
            }
            runCatching { system.ping() }
                .onSuccess { _state.update { GateState(checking = false, ready = true) } }
                .onFailure { e ->
                    _state.update { GateState(checking = false, error = e.userMessage()) }
                }
        }
    }
}

@Composable
fun GateScreen(onSetup: () -> Unit, onReady: () -> Unit) {
    val vm: GateViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.needsSetup, state.ready) {
        when {
            state.needsSetup -> onSetup()
            state.ready -> onReady()
        }
    }

    when {
        state.error != null -> ErrorState(
            message = "No se puede conectar con el servidor: ${state.error!!}",
            onRetry = vm::check
        ) {
            PillButton("Cambiar servidor", Icons.Outlined.Dns, onSetup)
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Logo(84)
                Text("TFM TV", style = MaterialTheme.typography.headlineMedium)
                Loading(Modifier.size(60.dp))
            }
        }
    }
}
