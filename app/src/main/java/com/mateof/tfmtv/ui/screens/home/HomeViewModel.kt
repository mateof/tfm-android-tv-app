package com.mateof.tfmtv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChatFolderDto
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.ChannelsRepository
import com.mateof.tfmtv.data.repo.ServerFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSection(val label: String) {
    START("Inicio"),
    SEARCH("Buscar"),
    LIBRARY("Biblioteca"),
    FAVORITES("Favoritos"),
    MINE("Mis canales"),
    SHARED("Compartidos"),
    FOLDERS("Carpetas"),
    ALL("Todos"),
    SETTINGS("Ajustes")
}

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val section: HomeSection = HomeSection.START,
    val channels: List<ChannelDto> = emptyList(),
    val folders: List<ChatFolderDto> = emptyList(),
    val openFolderId: Long? = null,
    val search: String = "",
    val showHidden: Boolean = false,
    val message: String? = null
) {
    val listed: List<ChannelDto> = channels.visible()
    val mine: List<ChannelDto> get() = listed.filter { it.isOwner }
    val favorites: List<ChannelDto> get() = listed.filter { it.isFavorite }

    fun channelCount(folder: ChatFolderDto): Int = folder.channels.visible().size

    private fun List<ChannelDto>.visible(): List<ChannelDto> =
        if (showHidden) this else filter { !it.isHidden }

    private fun channelsOf(section: HomeSection): List<ChannelDto> =
        when (section) {
            HomeSection.MINE -> mine
            HomeSection.SHARED -> listed.filter { !it.isOwner }
            HomeSection.FAVORITES -> favorites
            HomeSection.ALL -> listed
            HomeSection.FOLDERS -> folders.firstOrNull { it.id == openFolderId }?.channels?.visible().orEmpty()
            else -> emptyList()
        }

    /** The channels of a section that answer to the search box. */
    fun visibleChannels(section: HomeSection): List<ChannelDto> {
        val query = search.trim()
        val list = channelsOf(section)
        return if (query.isBlank()) list else list.filter { it.name.orEmpty().contains(query, ignoreCase = true) }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ChannelsRepository,
    private val prefs: ServerPreferences,
    private val features: ServerFeatures
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** The channel or folder a section was left through. */
    private val lastFocus = mutableMapOf<HomeSection, String>()

    init {
        viewModelScope.launch { features.load() }
        viewModelScope.launch {
            // Reloading is needed because hidden channels only come down the wire
            // when they are asked for.
            prefs.showHiddenChannels.collect { show ->
                _state.update { it.copy(showHidden = show) }
                load()
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = it.channels.isEmpty(), error = null) }
        val includeHidden = _state.value.showHidden
        viewModelScope.launch {
            runCatching {
                val channels = async { repo.all(includeHidden = includeHidden) }
                // Chat folders are optional: a server without them still works.
                val folders = async { runCatching { repo.folders().folders }.getOrDefault(emptyList()) }
                channels.await() to folders.await()
            }.onSuccess { (channels, folders) ->
                _state.update {
                    it.copy(loading = false, channels = channels, folders = folders)
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun select(section: HomeSection) =
        _state.update { it.copy(section = section, openFolderId = null, search = "") }

    fun openFolder(id: Long?) {
        id?.let { lastFocus[HomeSection.FOLDERS] = "folder-$it" }
        _state.update { it.copy(openFolderId = id, search = "") }
    }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    fun saveFocus(key: String) {
        lastFocus[_state.value.section] = key
    }

    fun focusOf(section: HomeSection): String? = lastFocus[section]

    fun toggleFavorite(channel: ChannelDto) = change(
        channel,
        { it.copy(isFavorite = !channel.isFavorite) },
        if (channel.isFavorite) "Quitado de favoritos" else "Añadido a favoritos"
    ) { repo.setFavorite(channel.id, !channel.isFavorite) }

    fun toggleHidden(channel: ChannelDto) = change(
        channel,
        { it.copy(isHidden = !channel.isHidden) },
        if (channel.isHidden) "El canal ya no está oculto" else "Canal oculto. Se vuelve a ver con Ajustes → Canales"
    ) { repo.setHidden(channel.id, !channel.isHidden) }

    /** Shown at once and put back if the server does not take it. */
    private fun change(channel: ChannelDto, edit: (ChannelDto) -> ChannelDto, done: String, call: suspend () -> Unit) {
        replace(channel.id, edit)
        viewModelScope.launch {
            runCatching { call() }
                .onSuccess { _state.update { it.copy(message = done) } }
                .onFailure { e ->
                    replace(channel.id) { channel }
                    _state.update { it.copy(message = "No se pudo: ${e.userMessage()}") }
                }
        }
    }

    private fun replace(id: Long, edit: (ChannelDto) -> ChannelDto) = _state.update { s ->
        fun List<ChannelDto>.edited() = map { if (it.id == id) edit(it) else it }
        s.copy(
            channels = s.channels.edited(),
            folders = s.folders.map { it.copy(channels = it.channels.edited()) }
        )
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
