package com.mateof.tfmtv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChatFolderDto
import com.mateof.tfmtv.data.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSection(val label: String) {
    MINE("Mis canales"),
    SHARED("Compartidos"),
    FAVORITES("Favoritos"),
    FOLDERS("Carpetas"),
    ALL("Todos"),
    SETTINGS("Ajustes")
}

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val section: HomeSection = HomeSection.MINE,
    val channels: List<ChannelDto> = emptyList(),
    val folders: List<ChatFolderDto> = emptyList(),
    val openFolderId: Long? = null,
    val search: String = ""
) {
    val mine: List<ChannelDto> get() = channels.filter { it.isOwner }
    val shared: List<ChannelDto> get() = channels.filter { !it.isOwner }
    val favorites: List<ChannelDto> get() = channels.filter { it.isFavorite }

    val visibleChannels: List<ChannelDto>
        get() {
            val list = when (section) {
                HomeSection.MINE -> mine
                HomeSection.SHARED -> shared
                HomeSection.FAVORITES -> favorites
                HomeSection.ALL -> channels
                HomeSection.FOLDERS ->
                    folders.firstOrNull { it.id == openFolderId }?.channels ?: emptyList()
                HomeSection.SETTINGS -> emptyList()
            }
            val query = search.trim()
            return if (query.isBlank()) list
            else list.filter { it.name.orEmpty().contains(query, ignoreCase = true) }
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ChannelsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val channels = async { repo.all() }
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

    fun openFolder(id: Long?) = _state.update { it.copy(openFolderId = id) }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }
}
