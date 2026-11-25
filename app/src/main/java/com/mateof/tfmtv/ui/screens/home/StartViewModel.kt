package com.mateof.tfmtv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.repo.LibraryActions
import com.mateof.tfmtv.data.repo.LibraryErrors
import com.mateof.tfmtv.data.repo.LibraryRepository
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.media.PlayLauncher
import com.mateof.tfmtv.ui.screens.library.displayTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class StartState(
    val loading: Boolean = true,
    /** Only when nothing at all could be read. */
    val error: String? = null,
    val continueFiles: List<LibraryFileDto> = emptyList(),
    /** Series with something watched and nothing half way: what comes next. */
    val nextSeries: List<LibraryItemDto> = emptyList(),
    val recentMovies: List<LibraryItemDto> = emptyList(),
    val recentSeries: List<LibraryItemDto> = emptyList(),
    val topMovies: List<LibraryItemDto> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class StartViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val actions: LibraryActions,
    private val launcher: PlayLauncher
) : ViewModel() {

    private val _state = MutableStateFlow(StartState())
    val state: StateFlow<StartState> = _state.asStateFlow()

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The card the screen was left through, as "row/card". */
    var lastFocus: String? = null

    /** Every entry to the screen: what was played meanwhile changes the first rows. */
    fun load() {
        _state.update { it.copy(error = null, loading = it.loading || it.error != null) }
        viewModelScope.launch {
            supervisorScope {
                val going = async { runCatching { repo.continueWatching() } }
                val movies = async { runCatching { repo.recent(LibraryKind.MOVIE, ROW) } }
                val series = async { runCatching { repo.recent(LibraryKind.SERIES, ROW) } }
                val played = async { runCatching { repo.top(LibraryKind.SERIES, "lastPlayed", ROW) } }
                val best = async { runCatching { repo.top(LibraryKind.MOVIE, "rating", 3 * ROW) } }

                val first = going.await()
                val recent = movies.await()
                // A library that is off is not an error: the rest of the screen still works.
                val failure = first.exceptionOrNull()?.takeIf { LibraryErrors.unavailableMessage(it) == null }
                if (failure != null && recent.isFailure) {
                    _state.update { it.copy(loading = false, error = failure.userMessage()) }
                    return@supervisorScope
                }
                val files = first.getOrDefault(emptyList())
                val half = files.mapNotNull { it.itemId }.toSet()
                val fresh = recent.getOrDefault(emptyList())
                _state.update { s ->
                    s.copy(
                        loading = false,
                        continueFiles = files.take(ROW),
                        recentMovies = fresh,
                        recentSeries = series.await().getOrDefault(emptyList()),
                        nextSeries = played.await().getOrDefault(emptyList()).filter {
                            it.watch.episodesWatched > 0 && !it.watch.completed && it.id !in half
                        },
                        topMovies = best.await().getOrDefault(emptyList())
                            .filter { !it.watch.completed && !it.watch.inProgress && (it.rating ?: 0.0) > 0 }
                            .take(ROW)
                    )
                }
            }
        }
    }

    fun play(file: LibraryFileDto, startMs: Long = file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L) {
        viewModelScope.launch {
            _events.send(launcher.resolve(file.file, file.channelId, file.displayTitle(), startMs))
        }
    }

    fun setWatched(file: LibraryFileDto, watched: Boolean) = change { actions.setWatched(file, watched) }
    fun setWatched(item: LibraryItemDto, watched: Boolean) = change { actions.setWatched(item, watched) }
    fun forget(file: LibraryFileDto) = change { actions.forget(file) }

    private fun change(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            val result = block()
            _state.update { it.copy(message = result.getOrElse { e -> e.message }) }
            if (result.isSuccess) load()
        }
    }

    fun say(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object {
        const val ROW = 20
    }
}
