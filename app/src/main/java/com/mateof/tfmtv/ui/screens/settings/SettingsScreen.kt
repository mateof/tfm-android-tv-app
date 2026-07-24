package com.mateof.tfmtv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.prefs.VideoPlayerChoice
import com.mateof.tfmtv.media.VideoPlayers
import com.mateof.tfmtv.ui.components.tapClick
import com.mateof.tfmtv.update.AppUpdater
import com.mateof.tfmtv.update.UpdateCheck
import com.mateof.tfmtv.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, ERROR }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val info: UpdateInfo? = null,
    val progress: Int = 0,
    val message: String? = null
)

data class SettingsState(
    val baseUrl: String = "",
    val videoPlayer: String = VideoPlayerChoice.INTERNAL,
    val videoApps: List<VideoPlayers.PlayerApp> = emptyList(),
    val appVersion: String = "",
    val update: UpdateState = UpdateState()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val players: VideoPlayers,
    private val updater: AppUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            baseUrl = prefs.current.baseUrl,
            videoPlayer = prefs.videoPlayer.value,
            videoApps = players.installed(),
            appVersion = updater.currentVersion
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.videoPlayer.collect { value -> _state.update { it.copy(videoPlayer = value) } }
        }
        viewModelScope.launch {
            prefs.configFlow.collect { c -> _state.update { it.copy(baseUrl = c.baseUrl) } }
        }
    }

    fun setVideoPlayer(value: String) = viewModelScope.launch { prefs.saveVideoPlayer(value) }

    fun checkUpdates() {
        _state.update { it.copy(update = UpdateState(phase = UpdatePhase.CHECKING)) }
        viewModelScope.launch {
            val update = when (val result = updater.check()) {
                UpdateCheck.UpToDate -> UpdateState(phase = UpdatePhase.UP_TO_DATE)
                is UpdateCheck.Available ->
                    UpdateState(phase = UpdatePhase.AVAILABLE, info = result.info)
                is UpdateCheck.Error ->
                    UpdateState(phase = UpdatePhase.ERROR, message = result.message)
            }
            _state.update { it.copy(update = update) }
        }
    }

    fun downloadAndInstall() {
        val info = _state.value.update.info ?: return
        if (!updater.canInstall()) {
            val opened = updater.requestInstallPermission()
            _state.update {
                it.copy(
                    update = it.update.copy(
                        phase = UpdatePhase.ERROR,
                        message = if (opened) {
                            "Autoriza la instalación de apps de esta fuente y vuelve a intentarlo"
                        } else {
                            "Este dispositivo no permite instalar apks desde la app"
                        }
                    )
                )
            }
            return
        }
        _state.update {
            it.copy(update = it.update.copy(phase = UpdatePhase.DOWNLOADING, progress = 0))
        }
        viewModelScope.launch {
            val file = updater.download(info) { pct ->
                _state.update {
                    it.copy(update = it.update.copy(progress = pct.coerceAtLeast(0)))
                }
            }
            if (file != null) {
                updater.install(file)
                _state.update { it.copy(update = UpdateState()) }
            } else {
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            phase = UpdatePhase.ERROR,
                            message = "No se pudo descargar la actualización"
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsContent(onReconfigure: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val options = buildList {
        add(VideoPlayerChoice.INTERNAL to "Reproductor interno")
        add(VideoPlayerChoice.ASK to "Preguntar cada vez")
        add(VideoPlayerChoice.SYSTEM to "Reproductor por defecto del sistema")
        state.videoApps.forEach { add(it.packageName to it.label) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, end = 80.dp, top = 32.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text(
                "Servidor: ${state.baseUrl.ifBlank { "sin configurar" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            Button(onClick = onReconfigure, modifier = Modifier.tapClick(onReconfigure)) {
                Text("Cambiar servidor")
            }
        }
        item {
            SectionTitle("Reproducción de vídeo")
        }
        items(options, key = { it.first }) { (value, label) ->
            ChoiceRow(
                label = label,
                selected = state.videoPlayer == value,
                onClick = { vm.setVideoPlayer(value) }
            )
        }
        item {
            SectionTitle("Aplicación")
        }
        item {
            Text(
                "Versión instalada: ${state.appVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            UpdateSection(
                update = state.update,
                onCheck = vm::checkUpdates,
                onInstall = vm::downloadAndInstall
            )
        }
    }
}

@Composable
private fun UpdateSection(
    update: UpdateState,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (update.phase) {
            UpdatePhase.CHECKING -> Status("Comprobando actualizaciones…")
            UpdatePhase.UP_TO_DATE -> Status("Ya tienes la última versión.")
            UpdatePhase.DOWNLOADING -> Status("Descargando… ${update.progress}%")
            UpdatePhase.ERROR -> Status(
                update.message ?: "Error comprobando actualizaciones",
                MaterialTheme.colorScheme.error
            )
            UpdatePhase.AVAILABLE -> {
                Status("Nueva versión disponible: ${update.info?.versionName}")
                update.info?.notes?.takeIf { it.isNotBlank() }?.let { Status(it) }
            }
            UpdatePhase.IDLE -> Unit
        }

        if (update.phase == UpdatePhase.AVAILABLE) {
            Button(onClick = onInstall, modifier = Modifier.tapClick(onInstall)) {
                Text("Descargar e instalar")
            }
        }
        if (update.phase != UpdatePhase.CHECKING && update.phase != UpdatePhase.DOWNLOADING) {
            Button(onClick = onCheck, modifier = Modifier.tapClick(onCheck)) {
                Text("Buscar actualizaciones")
            }
        }
    }
}

@Composable
private fun Status(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(20.dp)) {
                if (selected) {
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                }
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
