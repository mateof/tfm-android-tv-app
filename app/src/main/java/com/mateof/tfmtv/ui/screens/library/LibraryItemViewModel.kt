package com.mateof.tfmtv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.LibraryEpisodeDto
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDetailDto
import com.mateof.tfmtv.data.repo.LibraryActions
import com.mateof.tfmtv.data.repo.LibraryRepository
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.media.PlayLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryItemState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: LibraryItemDetailDto? = null,
    val season: Int? = null,
    val busy: Boolean = false,
    val message: String? = null
) {
    val episodes: List<LibraryEpisodeDto>
        get() = item?.seasons?.firstOrNull { it.number == season }?.episodes.orEmpty()
}

@HiltViewModel
class LibraryItemViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val actions: LibraryActions,
    private val launcher: PlayLauncher
) : ViewModel() {

    /** The episode or version the screen was left through. */
    var lastFocus: String? = null

    private var itemId: String = ""

    private val _state = MutableStateFlow(LibraryItemState())
    val state: StateFlow<LibraryItemState> = _state.asStateFlow()

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load(id: String) {
        itemId = id
        _state.update { it.copy(loading = it.item == null, error = null) }
        viewModelScope.launch {
            runCatching { repo.item(id) }
                .onSuccess { detail ->
                    _state.update { s ->
                        val season = s.season?.takeIf { n -> detail.seasons.any { it.number == n } }
                            ?: detail.nextUp?.season
                            ?: detail.seasons.firstOrNull()?.number
                        s.copy(loading = false, item = detail, season = season)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        // With the item on screen a failed reload is only a notice.
                        if (it.item == null) it.copy(loading = false, error = e.userMessage())
                        else it.copy(loading = false, message = e.userMessage())
                    }
                }
        }
    }

    fun selectSeason(number: Int) = _state.update { it.copy(season = number) }

    /** The file "Reproducir" would start: what is half watched, else the first thing to see. */
    fun primaryFile(): Pair<LibraryFileDto, Long>? {
        val item = _state.value.item ?: return null
        item.resume?.let { return it to (it.watch?.positionMs ?: 0L) }
        if (item.isSeries) {
            val episode = item.nextUp ?: item.seasons.firstOrNull()?.episodes?.firstOrNull() ?: return null
            return episodeFile(episode)
        }
        val file = item.files.firstOrNull() ?: return null
        return file to 0L
    }

    fun episodeFile(episode: LibraryEpisodeDto): Pair<LibraryFileDto, Long>? {
        val file = episode.files.firstOrNull() ?: return null
        val start = episode.watch?.takeIf { it.inProgress && it.fileId == file.fileId }?.positionMs
            ?: file.watch?.takeIf { it.inProgress }?.positionMs
            ?: 0L
        return file to start
    }

    fun titleFor(file: LibraryFileDto): String {
        val item = _state.value.item
        return when {
            item == null -> file.fileName
            item.isSeries && file.episode != null -> "${item.title} · T${file.season ?: 1} E${file.episode}"
            else -> item.title
        }
    }

    fun play(file: LibraryFileDto, startMs: Long) {
        viewModelScope.launch {
            _events.send(launcher.resolve(file.file, file.channelId, titleFor(file), startMs))
        }
    }

    fun toggleWatched() {
        val item = _state.value.item ?: return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repo.setItemWatched(item.id, !item.watch.completed) }
                .onSuccess { updated ->
                    _state.update { it.copy(busy = false, item = updated ?: it.item) }
                    if (updated == null) load(item.id)
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }

    fun setSeasonWatched(season: Int, watched: Boolean) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            runCatching { repo.setItemWatched(item.id, watched, season) }
                .onSuccess { updated ->
                    _state.update {
                        it.copy(item = updated ?: it.item, message = "Temporada $season marcada como ${if (watched) "vista" else "no vista"}")
                    }
                    if (updated == null) load(item.id)
                }
                .onFailure { e -> say("No se pudo: ${e.userMessage()}") }
        }
    }

    fun setWatched(file: LibraryFileDto, watched: Boolean) {
        viewModelScope.launch {
            val result = actions.setWatched(file, watched)
            say(result.getOrElse { it.message.orEmpty() })
            if (result.isSuccess) load(itemId)
        }
    }

    /** Asks the provider for the metadata and the episodes again. */
    fun refreshMetadata() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = "Actualizando los datos…") }
        viewModelScope.launch {
            runCatching { repo.refreshItem(itemId) }
                .onSuccess {
                    _state.update { it.copy(busy = false, message = "Datos actualizados") }
                    load(itemId)
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "No se pudo: ${e.userMessage()}") } }
        }
    }

    fun say(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }
}
