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

enum class SortField(val query: String) { NAME("name"), DATE("date"), SIZE("size") }

enum class SortOption(val label: String, val field: SortField, val descending: Boolean) {
    NAME_ASC("Nombre A-Z", SortField.NAME, false),
    NAME_DESC("Nombre Z-A", SortField.NAME, true),
    NEWEST("Más nuevos", SortField.DATE, true),
    OLDEST("Más antiguos", SortField.DATE, false),
    LARGEST("Más grandes", SortField.SIZE, true),
    SMALLEST("Más pequeños", SortField.SIZE, false)
}

private val defaultSorts = mapOf(
    ChannelTab.FOLDERS to SortOption.NAME_ASC,
    ChannelTab.VIDEOS to SortOption.NEWEST,
    ChannelTab.MESSAGES to SortOption.NEWEST
)

data class ChannelState(
    val tab: ChannelTab = ChannelTab.FOLDERS,
    val loading: Boolean = true,
    val error: String? = null,
    val path: String = "/",
    val folderId: String? = null,
    val parentFolderId: String? = null,
    val parentPath: String? = null,
    val breadcrumbs: List<BreadcrumbDto> = emptyList(),
    val folders: List<ApiFileDto> = emptyList(),
    val videos: List<ApiFileDto> = emptyList(),
    val allVideos: List<ApiFileDto> = emptyList(),
    val messages: List<ChannelMessageDto> = emptyList(),
    val filesByMessage: Map<Long, ApiFileDto> = emptyMap(),
    val resolving: Boolean = false,
    val sorts: Map<ChannelTab, SortOption> = defaultSorts
) {
    val canGoUp: Boolean get() = parentFolderId != null || parentPath != null

    val sort: SortOption get() = sorts[tab] ?: SortOption.NAME_ASC

    /** The messages endpoint has no sort parameter, so they are ordered here. */
    val sortedMessages: List<ChannelMessageDto>
        get() {
            val option = sorts[ChannelTab.MESSAGES] ?: SortOption.NEWEST
            val ascending = when (option.field) {
                SortField.NAME -> messages.sortedBy { it.fileName.orEmpty().lowercase() }
                SortField.DATE -> messages.sortedBy { it.date.orEmpty() }
                SortField.SIZE -> messages.sortedBy { it.fileSize ?: 0L }
            }
            return if (option.descending) ascending.asReversed() else ascending
        }
}

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
        openFolder(path = "/")
    }

    fun selectTab(tab: ChannelTab) {
        _state.update { it.copy(tab = tab, error = null) }
        when (tab) {
            ChannelTab.FOLDERS -> if (_state.value.folders.isEmpty() && _state.value.videos.isEmpty()) {
                openFolder(folderId = _state.value.folderId, path = _state.value.path)
            }
            ChannelTab.VIDEOS -> if (_state.value.allVideos.isEmpty()) loadAllVideos()
            ChannelTab.MESSAGES -> if (_state.value.messages.isEmpty()) loadMessages()
        }
    }

    fun setSort(option: SortOption) {
        val tab = _state.value.tab
        if (_state.value.sort == option) return
        _state.update { it.copy(sorts = it.sorts + (tab to option)) }
        when (tab) {
            ChannelTab.FOLDERS ->
                openFolder(folderId = _state.value.folderId, path = _state.value.path)
            ChannelTab.VIDEOS -> loadAllVideos()
            // Messages are already loaded; ChannelState sorts them.
            ChannelTab.MESSAGES -> Unit
        }
    }

    /**
     * Navigation goes by folder id: the `path` an item carries is its *containing*
     * folder, so using it would always walk back to the level above.
     */
    fun openFolder(folderId: String? = null, path: String? = null) {
        _state.update { it.copy(loading = true, error = null) }
        val sort = _state.value.sorts[ChannelTab.FOLDERS] ?: SortOption.NAME_ASC
        viewModelScope.launch {
            runCatching {
                repo.browse(
                    channelId,
                    path = path,
                    folderId = folderId,
                    sortBy = sort.field.query,
                    sortDescending = sort.descending
                )
            }
                .onSuccess { contents ->
                    _state.update {
                        it.copy(
                            loading = false,
                            path = contents.currentPath ?: "/",
                            folderId = contents.currentFolderId,
                            parentFolderId = contents.parentFolderId,
                            parentPath = contents.parentPath,
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
        val state = _state.value
        if (!state.canGoUp) return
        openFolder(folderId = state.parentFolderId, path = state.parentPath)
    }

    private fun loadAllVideos() {
        _state.update { it.copy(loading = true, error = null) }
        val sort = _state.value.sorts[ChannelTab.VIDEOS] ?: SortOption.NEWEST
        viewModelScope.launch {
            runCatching {
                repo.allVideos(
                    channelId,
                    sortBy = sort.field.query,
                    sortDescending = sort.descending
                )
            }
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
