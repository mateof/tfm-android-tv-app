package com.mateof.tfmtv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryFileStatus
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.repo.LibraryErrors
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

enum class LibraryTab(val label: String) {
    CONTINUE("Continuar"),
    MOVIES("Películas"),
    SERIES("Series"),
    REVIEW("Revisar")
}

data class LibraryState(
    val tab: LibraryTab = LibraryTab.CONTINUE,
    val loading: Boolean = false,
    val error: String? = null,
    /** Library switched off or missing on the server: a hint, not an error. */
    val unavailable: String? = null,
    val search: String = "",
    val continueFiles: List<LibraryFileDto> = emptyList(),
    val movies: List<LibraryItemDto> = emptyList(),
    val series: List<LibraryItemDto> = emptyList(),
    val reviewFiles: List<LibraryFileDto> = emptyList(),
    val loaded: Set<LibraryTab> = emptySet()
) {
    private val query: String get() = search.trim()

    val visibleContinue: List<LibraryFileDto>
        get() = continueFiles.filter { query.isBlank() || it.displayTitle().contains(query, ignoreCase = true) }

    val visibleItems: List<LibraryItemDto>
        get() = (if (tab == LibraryTab.MOVIES) movies else series).filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true)
                || it.originalTitle?.contains(query, ignoreCase = true) == true
        }

    val visibleReview: List<LibraryFileDto>
        get() = reviewFiles.filter {
            query.isBlank() || it.fileName.contains(query, ignoreCase = true)
                || it.parsed.title.contains(query, ignoreCase = true)
        }
}

fun LibraryFileDto.displayTitle(): String {
    val item = item ?: return fileName
    return if (item.kind == LibraryKind.SERIES && episode != null) {
        "${item.title} · T${season ?: 1} E$episode"
    } else item.title
}

/** Where a tab was left: what was on screen and which card the user left through. */
data class TabPosition(val index: Int = 0, val offset: Int = 0, val key: String? = null)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val launcher: PlayLauncher
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Scroll and focus per tab. The ViewModel outlives the screen while Home is
    // on the back stack, so opening a detail and coming back lands on the same
    // place instead of at the top of the grid.
    private val positions = mutableMapOf<LibraryTab, TabPosition>()

    fun position(tab: LibraryTab): TabPosition = positions[tab] ?: TabPosition()

    fun saveScroll(tab: LibraryTab, index: Int, offset: Int) {
        positions[tab] = position(tab).copy(index = index, offset = offset)
    }

    /** The card the user opened; focus returns to it. */
    fun saveFocus(tab: LibraryTab, key: String) {
        positions[tab] = position(tab).copy(key = key)
    }

    fun selectTab(tab: LibraryTab) {
        _state.update { it.copy(tab = tab, error = null, unavailable = null) }
        if (tab !in _state.value.loaded) load(tab)
    }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    /**
     * Reloads the visible tab, e.g. after playing or fixing something elsewhere.
     * Keeps the current list on screen: swapping it for a spinner would drop the
     * grid, and with it the scroll position the user expects to come back to.
     */
    fun refresh() = load(_state.value.tab, keepContent = true)

    fun load(tab: LibraryTab = _state.value.tab, keepContent: Boolean = false) {
        val spinner = !keepContent || tab !in _state.value.loaded
        _state.update { it.copy(loading = spinner, error = null, unavailable = null) }
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    LibraryTab.CONTINUE -> {
                        val list = repo.continueWatching()
                        _state.update { it.copy(continueFiles = list) }
                    }
                    LibraryTab.MOVIES -> {
                        val list = repo.items(LibraryKind.MOVIE)
                        _state.update { it.copy(movies = list) }
                    }
                    LibraryTab.SERIES -> {
                        val list = repo.items(LibraryKind.SERIES)
                        _state.update { it.copy(series = list) }
                    }
                    LibraryTab.REVIEW -> {
                        val review = repo.files(LibraryFileStatus.REVIEW)
                        val unmatched = repo.files(LibraryFileStatus.UNMATCHED)
                        _state.update { it.copy(reviewFiles = review + unmatched) }
                    }
                }
            }.onSuccess {
                _state.update { it.copy(loading = false, loaded = it.loaded + tab) }
            }.onFailure { e ->
                val unavailable = LibraryErrors.unavailableMessage(e)
                _state.update {
                    it.copy(
                        loading = false,
                        unavailable = unavailable,
                        error = if (unavailable == null) e.userMessage() else null
                    )
                }
            }
        }
    }

    /** Continue watching: resumes where the server says it stopped. */
    fun play(file: LibraryFileDto) {
        viewModelScope.launch {
            val start = file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L
            _events.send(launcher.resolve(file.file, file.channelId, file.displayTitle(), start))
        }
    }
}
