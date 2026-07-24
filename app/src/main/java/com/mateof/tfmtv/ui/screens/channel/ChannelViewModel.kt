package com.mateof.tfmtv.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.BreadcrumbDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.repo.MediaUrls
import com.mateof.tfmtv.data.repo.VideoRepository
import com.mateof.tfmtv.media.VideoPlayers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChannelTab(val label: String) {
    FOLDERS("Carpetas"),
    VIDEOS("Todos los vídeos"),
    MESSAGES("Mensajes")
}

data class ChannelState(
    val tab: ChannelTab = ChannelTab.FOLDERS,
    val loading: Boolean = true,
    val error: String? = null,
    val path: String = "/",
    val breadcrumbs: List<BreadcrumbDto> = emptyList(),
    val folders: List<ApiFileDto> = emptyList(),
    val videos: List<ApiFileDto> = emptyList(),
    val allVideos: List<ApiFileDto> = emptyList(),
    val messages: List<ChannelMessageDto> = emptyList(),
    val filesByMessage: Map<Long, ApiFileDto> = emptyMap(),
    val resolving: Boolean = false
)

sealed interface PlayEvent {
    data class Internal(val url: String, val title: String) : PlayEvent
    data object Handed : PlayEvent
    data class Failed(val message: String) : PlayEvent
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repo: VideoRepository,
    private val mediaUrls: MediaUrls,
    private val players: VideoPlayers
) : ViewModel() {

    private var channelId: Long = 0

    private val _state = MutableStateFlow(ChannelState())
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun start(id: Long) {
        if (channelId == id) return
        channelId = id
        openFolder("/")
    }

    fun selectTab(tab: ChannelTab) {
        _state.update { it.copy(tab = tab, error = null) }
        when (tab) {
            ChannelTab.FOLDERS -> if (_state.value.folders.isEmpty() && _state.value.videos.isEmpty()) {
                openFolder(_state.value.path)
            }
            ChannelTab.VIDEOS -> if (_state.value.allVideos.isEmpty()) loadAllVideos()
            ChannelTab.MESSAGES -> if (_state.value.messages.isEmpty()) loadMessages()
        }
    }

    fun openFolder(path: String) {
        _state.update { it.copy(loading = true, error = null, path = path) }
        viewModelScope.launch {
            runCatching { repo.browse(channelId, path = path) }
                .onSuccess { contents ->
                    _state.update {
                        it.copy(
                            loading = false,
                            path = contents.currentPath ?: path,
                            breadcrumbs = contents.breadcrumbs,
                            folders = contents.items.filter { item -> !item.isFile },
                            videos = contents.items.filter { item -> item.isFile }
                        )
                    }
                }
                .onFailure { e -> fail(e) }
        }
    }

    fun up() {
        val current = _state.value.path
        if (current == "/" || current.isBlank()) return
        val parent = current.trimEnd('/').substringBeforeLast('/', "").ifBlank { "/" }
        openFolder(parent)
    }

    private fun loadAllVideos() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.allVideos(channelId) }
                .onSuccess { list -> _state.update { it.copy(loading = false, allVideos = list) } }
                .onFailure { e -> fail(e) }
        }
    }

    private fun loadMessages() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val index = async { repo.videoIndex(channelId) }
            runCatching { repo.videoMessages(channelId) }
                .onSuccess { list ->
                    val byMessage = index.await()
                    _state.update {
                        it.copy(loading = false, messages = list, filesByMessage = byMessage)
                    }
                }
                .onFailure { e -> fail(e) }
        }
    }

    fun play(file: ApiFileDto) {
        val url = mediaUrls.withKey(file.streamUrl ?: file.downloadUrl)
        if (url == null) {
            emit(PlayEvent.Failed("Este vídeo no tiene URL de reproducción"))
            return
        }
        if (players.launchExternal(url, file.name)) emit(PlayEvent.Handed)
        else emit(PlayEvent.Internal(url, file.name))
    }

    /** Messages only carry a name, so the indexed file has to be looked up first. */
    fun play(message: ChannelMessageDto) {
        fileFor(message)?.let { return play(it) }
        _state.update { it.copy(resolving = true) }
        viewModelScope.launch {
            val file = runCatching { repo.resolveMessage(channelId, message) }.getOrNull()
            _state.update { it.copy(resolving = false) }
            if (file == null) {
                emit(PlayEvent.Failed("El vídeo del mensaje no está indexado en el canal"))
            } else {
                play(file)
            }
        }
    }

    private fun fileFor(message: ChannelMessageDto): ApiFileDto? =
        _state.value.filesByMessage[message.id]

    private fun emit(event: PlayEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun fail(e: Throwable) {
        _state.update { it.copy(loading = false, error = e.userMessage()) }
    }
}
