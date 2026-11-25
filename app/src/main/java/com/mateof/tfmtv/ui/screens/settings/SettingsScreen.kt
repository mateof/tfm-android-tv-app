package com.mateof.tfmtv.ui.screens.settings

import com.mateof.tfmtv.ui.components.listRowBorder
import com.mateof.tfmtv.ui.components.listRowColors
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.data.prefs.AccentChoice
import com.mateof.tfmtv.data.prefs.CastRouteMode
import com.mateof.tfmtv.data.prefs.EffectsMode
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.prefs.VideoPlayerChoice
import com.mateof.tfmtv.data.repo.ServerFeatures
import com.mateof.tfmtv.media.VideoPlayers
import com.mateof.tfmtv.ui.components.ContentEnd
import com.mateof.tfmtv.ui.components.ContentStart
import com.mateof.tfmtv.ui.components.OptionPicker
import com.mateof.tfmtv.ui.components.PickerOption
import com.mateof.tfmtv.ui.components.PillButton
import com.mateof.tfmtv.ui.components.ProgressBar
import com.mateof.tfmtv.ui.components.entryFocus
import com.mateof.tfmtv.ui.components.tapClick
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.paletteOf
import com.mateof.tfmtv.ui.theme.palette
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
    val serverVersion: String? = null,
    val features: Int = 0,
    val videoPlayer: String = VideoPlayerChoice.INTERNAL,
    val videoApps: List<VideoPlayers.PlayerApp> = emptyList(),
    val showHiddenChannels: Boolean = false,
    val castRoute: CastRouteMode = CastRouteMode.AUTO,
    val accent: AccentChoice = AccentChoice.OCEAN,
    val effects: EffectsMode = EffectsMode.AUTO,
    val thumbnails: Boolean = true,
    val autoplay: Boolean = true,
    val appVersion: String = "",
    val update: UpdateState = UpdateState()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val players: VideoPlayers,
    private val updater: AppUpdater,
    features: ServerFeatures
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            baseUrl = prefs.current.baseUrl,
            videoPlayer = prefs.videoPlayer.value,
            videoApps = players.installed(),
            showHiddenChannels = prefs.showHiddenChannels.value,
            castRoute = prefs.castRoute.value,
            accent = prefs.accent.value,
            effects = prefs.effects.value,
            thumbnails = prefs.thumbnails.value,
            autoplay = prefs.autoplayNext.value,
            appVersion = updater.currentVersion
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { prefs.videoPlayer.collect { v -> _state.update { it.copy(videoPlayer = v) } } }
        viewModelScope.launch { prefs.configFlow.collect { c -> _state.update { it.copy(baseUrl = c.baseUrl) } } }
        viewModelScope.launch { prefs.castRoute.collect { v -> _state.update { it.copy(castRoute = v) } } }
        viewModelScope.launch { prefs.showHiddenChannels.collect { v -> _state.update { it.copy(showHiddenChannels = v) } } }
        viewModelScope.launch { prefs.accent.collect { v -> _state.update { it.copy(accent = v) } } }
        viewModelScope.launch { prefs.effects.collect { v -> _state.update { it.copy(effects = v) } } }
        viewModelScope.launch { prefs.thumbnails.collect { v -> _state.update { it.copy(thumbnails = v) } } }
        viewModelScope.launch { prefs.autoplayNext.collect { v -> _state.update { it.copy(autoplay = v) } } }
        viewModelScope.launch {
            features.capabilities.collect { c -> _state.update { it.copy(serverVersion = c.serverVersion, features = c.features.size) } }
        }
    }

    fun setVideoPlayer(value: String) = viewModelScope.launch { prefs.saveVideoPlayer(value) }
    fun setCastRoute(value: CastRouteMode) = viewModelScope.launch { prefs.saveCastRoute(value) }
    fun setAccent(value: AccentChoice) = viewModelScope.launch { prefs.saveAccent(value) }
    fun setEffects(value: EffectsMode) = viewModelScope.launch { prefs.saveEffects(value) }
    fun toggleThumbnails() = viewModelScope.launch { prefs.saveThumbnails(!_state.value.thumbnails) }
    fun toggleAutoplay() = viewModelScope.launch { prefs.saveAutoplayNext(!_state.value.autoplay) }
    fun toggleHiddenChannels() = viewModelScope.launch { prefs.saveShowHiddenChannels(!_state.value.showHiddenChannels) }

    fun checkUpdates() {
        _state.update { it.copy(update = UpdateState(phase = UpdatePhase.CHECKING)) }
        viewModelScope.launch {
            val update = when (val result = updater.check()) {
                UpdateCheck.UpToDate -> UpdateState(phase = UpdatePhase.UP_TO_DATE)
                is UpdateCheck.Available -> UpdateState(phase = UpdatePhase.AVAILABLE, info = result.info)
                is UpdateCheck.Error -> UpdateState(phase = UpdatePhase.ERROR, message = result.message)
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
                        message = if (opened) "Autoriza la instalación de apps de esta fuente y vuelve a intentarlo"
                        else "Este dispositivo no permite instalar apks desde la app"
                    )
                )
            }
            return
        }
        _state.update { it.copy(update = it.update.copy(phase = UpdatePhase.DOWNLOADING, progress = 0)) }
        viewModelScope.launch {
            val file = updater.download(info) { pct ->
                _state.update { it.copy(update = it.update.copy(progress = pct.coerceAtLeast(0))) }
            }
            if (file != null) {
                updater.install(file)
                _state.update { it.copy(update = UpdateState()) }
            } else {
                _state.update {
                    it.copy(update = it.update.copy(phase = UpdatePhase.ERROR, message = "No se pudo descargar la actualización"))
                }
            }
        }
    }
}

private enum class Category(val label: String, val icon: ImageVector) {
    SERVER("Servidor", Icons.Outlined.Dns),
    PLAYBACK("Reproducción", Icons.Outlined.PlayCircle),
    LOOK("Apariencia", Icons.Outlined.Palette),
    CHANNELS("Canales", Icons.Outlined.Subscriptions),
    CAST("Envío a la tele", Icons.Outlined.Cast),
    APP("Aplicación", Icons.Outlined.Info)
}

private enum class Choice { PLAYER, ACCENT, EFFECTS, CAST }

/** Categories on the left, their settings on the right; moving over a category shows it. */
@Composable
fun SettingsContent(onReconfigure: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(Category.SERVER) }
    var choice by remember { mutableStateOf<Choice?>(null) }

    Row(Modifier.fillMaxSize().padding(start = ContentStart, end = ContentEnd, top = 26.dp)) {
        Column(Modifier.width(230.dp).fillMaxHeight().focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 14.dp))
            Category.entries.forEach { c ->
                CategoryRow(
                    c,
                    selected = category == c,
                    onFocus = { category = c },
                    modifier = Modifier.entryFocus(c == category)
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(start = 28.dp),
            contentPadding = PaddingValues(top = 50.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                when (category) {
                    Category.SERVER -> Section("Servidor") {
                        Info("Dirección", state.baseUrl.ifBlank { "sin configurar" })
                        Info(
                            "Versión",
                            state.serverVersion?.let { "$it · ${state.features} funciones opcionales" }
                                ?: "Servidor antiguo: sin búsqueda global, miniaturas ni mensajes sin indexar"
                        )
                        Row(Modifier.padding(top = 6.dp)) {
                            PillButton("Cambiar servidor", Icons.Outlined.Dns, onReconfigure, primary = true)
                        }
                    }

                    Category.PLAYBACK -> Section("Reproducción") {
                        val player = playerOptions(state).firstOrNull { it.value == state.videoPlayer }?.label ?: state.videoPlayer
                        Setting("Reproductor de vídeo", player, onClick = { choice = Choice.PLAYER })
                        Toggle(
                            "Siguiente episodio automático",
                            "Al acabar un episodio empieza el siguiente; lo ofrece 25 s antes del final.",
                            state.autoplay,
                            vm::toggleAutoplay
                        )
                        Note("El idioma del audio y de los subtítulos que elijas en el reproductor se recuerda para los siguientes vídeos.")
                    }

                    Category.LOOK -> Section("Apariencia") {
                        Setting("Color", state.accent.label, onClick = { choice = Choice.ACCENT }) {
                            AccentDot(state.accent)
                        }
                        Setting("Efectos", state.effects.label, onClick = { choice = Choice.EFFECTS })
                        Note("Los efectos son el brillo de las tarjetas, los fondos que cambian con la selección y las transiciones. En automático se apagan en los aparatos con poca memoria o con Android 7 o anterior.")
                        Toggle(
                            "Miniaturas de los vídeos",
                            "La vista previa que Telegram guarda en cada mensaje. El servidor no descarga el vídeo para sacarla.",
                            state.thumbnails,
                            vm::toggleThumbnails
                        )
                    }

                    Category.CHANNELS -> Section("Canales") {
                        Toggle(
                            "Mostrar canales ocultos",
                            "Los canales ocultos desde la web o con una pulsación larga sobre el canal.",
                            state.showHiddenChannels,
                            vm::toggleHiddenChannels
                        )
                    }

                    Category.CAST -> Section("Envío a la tele (DLNA)") {
                        Setting("Ruta del vídeo", state.castRoute.label, onClick = { choice = Choice.CAST })
                        Note(
                            "Automático elige solo: directo si la tele llega al servidor, y a través de este aparato " +
                                "si el servidor sólo es accesible desde aquí (por ejemplo, por una VPN)."
                        )
                    }

                    Category.APP -> Section("Aplicación") {
                        Info("Versión instalada", state.appVersion)
                        UpdateSection(state.update, vm::checkUpdates, vm::downloadAndInstall)
                    }
                }
            }
        }
    }

    when (choice) {
        Choice.PLAYER -> OptionPicker(
            "Reproductor de vídeo", playerOptions(state), state.videoPlayer, { vm.setVideoPlayer(it) }, { choice = null }
        )
        Choice.ACCENT -> OptionPicker(
            "Color", AccentChoice.entries.map { PickerOption(it, it.label) }, state.accent, { vm.setAccent(it) }, { choice = null }
        )
        Choice.EFFECTS -> OptionPicker(
            "Efectos", EffectsMode.entries.map { PickerOption(it, it.label) }, state.effects, { vm.setEffects(it) }, { choice = null }
        )
        Choice.CAST -> OptionPicker(
            "Ruta del vídeo", CastRouteMode.entries.map { PickerOption(it, it.label) }, state.castRoute, { vm.setCastRoute(it) }, { choice = null }
        )
        null -> Unit
    }
}

private fun playerOptions(state: SettingsState) = buildList {
    add(PickerOption(VideoPlayerChoice.INTERNAL, "Reproductor interno"))
    add(PickerOption(VideoPlayerChoice.ASK, "Preguntar cada vez"))
    add(PickerOption(VideoPlayerChoice.SYSTEM, "El del sistema"))
    state.videoApps.forEach { add(PickerOption(it.packageName, it.label)) }
}

@Composable
private fun CategoryRow(category: Category, selected: Boolean, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onFocus,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .tapClick(onFocus),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
            contentColor = if (selected) palette.accent else OnSurfaceMuted,
            focusedContainerColor = palette.accent,
            focusedContentColor = palette.onAccent
        )
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(category.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(category.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun rowColors() = listRowColors()

/** A setting whose value is picked from a list. */
@Composable
private fun Setting(label: String, value: String, onClick: () -> Unit, leading: @Composable () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = listRowBorder(),
        colors = rowColors()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            leading()
            Text(value, style = MaterialTheme.typography.bodyMedium, color = palette.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Toggle(label: String, detail: String?, on: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().tapClick(onToggle),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = listRowBorder(),
        colors = rowColors()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted) }
            }
            Switch(on)
        }
    }
}

@Composable
private fun Switch(on: Boolean) {
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (on) palette.horizontal else androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.2f))),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(3.dp).size(18.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun AccentDot(choice: AccentChoice) {
    Box(Modifier.size(18.dp).clip(CircleShape).background(paletteOf(choice, false).gradient))
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

@Composable
private fun UpdateSection(update: UpdateState, onCheck: () -> Unit, onInstall: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        when (update.phase) {
            UpdatePhase.CHECKING -> Note("Comprobando actualizaciones…")
            UpdatePhase.UP_TO_DATE -> Note("Ya tienes la última versión.")
            UpdatePhase.DOWNLOADING -> {
                Note("Descargando… ${update.progress}%")
                ProgressBar(update.progress / 100f, Modifier.width(320.dp))
            }
            UpdatePhase.ERROR -> Text(
                update.message ?: "Error comprobando actualizaciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            UpdatePhase.AVAILABLE -> {
                Text("Nueva versión disponible: ${update.info?.versionName}", style = MaterialTheme.typography.titleMedium, color = palette.accent)
                update.info?.notes?.takeIf { it.isNotBlank() }?.let { Note(it.take(600)) }
            }
            UpdatePhase.IDLE -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (update.phase == UpdatePhase.AVAILABLE) PillButton("Descargar e instalar", null, onInstall, primary = true)
            if (update.phase != UpdatePhase.CHECKING && update.phase != UpdatePhase.DOWNLOADING) {
                PillButton("Buscar actualizaciones", null, onCheck)
            }
        }
    }
}
