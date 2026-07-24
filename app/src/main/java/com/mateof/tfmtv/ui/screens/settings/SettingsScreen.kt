package com.mateof.tfmtv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.mateof.tfmtv.media.VideoThumbnails
import com.mateof.tfmtv.ui.components.tapClick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val baseUrl: String = "",
    val videoPlayer: String = VideoPlayerChoice.INTERNAL,
    val videoApps: List<VideoPlayers.PlayerApp> = emptyList(),
    val thumbnails: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val players: VideoPlayers,
    private val thumbs: VideoThumbnails
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            baseUrl = prefs.current.baseUrl,
            videoPlayer = prefs.videoPlayer.value,
            videoApps = players.installed(),
            thumbnails = prefs.thumbnails.value
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.videoPlayer.collect { value -> _state.update { it.copy(videoPlayer = value) } }
        }
        viewModelScope.launch {
            prefs.thumbnails.collect { value -> _state.update { it.copy(thumbnails = value) } }
        }
        viewModelScope.launch {
            prefs.configFlow.collect { c -> _state.update { it.copy(baseUrl = c.baseUrl) } }
        }
    }

    fun setVideoPlayer(value: String) = viewModelScope.launch { prefs.saveVideoPlayer(value) }

    fun setThumbnails(value: Boolean) = viewModelScope.launch {
        prefs.saveThumbnails(value)
        if (!value) thumbs.clear()
    }

    fun clearThumbnails() = thumbs.clear()
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
            SectionTitle("Previsualizaciones")
        }
        item {
            ChoiceRow(
                label = "Mostrar miniaturas de los vídeos",
                selected = state.thumbnails,
                onClick = { vm.setThumbnails(!state.thumbnails) }
            )
        }
        item {
            Text(
                "Las miniaturas se extraen del propio vídeo; desactívalas si el " +
                    "dispositivo va justo de potencia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Button(
                onClick = vm::clearThumbnails,
                modifier = Modifier.tapClick(vm::clearThumbnails)
            ) {
                Text("Vaciar caché de miniaturas")
            }
        }
    }
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
