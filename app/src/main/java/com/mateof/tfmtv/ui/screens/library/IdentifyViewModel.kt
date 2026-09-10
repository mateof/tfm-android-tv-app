package com.mateof.tfmtv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.IdentifyItemRequest
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.model.MatchFileRequest
import com.mateof.tfmtv.data.model.ProviderCandidateDto
import com.mateof.tfmtv.data.repo.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentifyState(
    val target: IdentifyTarget = IdentifyTarget(),
    val query: String = "",
    val kind: String = LibraryKind.MOVIE,
    val season: String = "",
    val episode: String = "",
    val searching: Boolean = false,
    val results: List<ProviderCandidateDto> = emptyList(),
    val searched: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
) {
    val isFile: Boolean get() = target.fileId != null && target.channelId != null
    val needsEpisode: Boolean get() = isFile && kind == LibraryKind.SERIES
}

@HiltViewModel
class IdentifyViewModel @Inject constructor(
    private val repo: LibraryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(IdentifyState())
    val state: StateFlow<IdentifyState> = _state.asStateFlow()

    /** Emits once the correction has been saved. */
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var searchJob: Job? = null
    private var started = false

    fun start(target: IdentifyTarget) {
        if (started) return
        started = true
        _state.update {
            it.copy(
                target = target,
                query = target.query,
                kind = target.kind,
                season = target.season?.toString() ?: "1",
                episode = target.episode?.toString().orEmpty()
            )
        }
        search()
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        search(debounce = true)
    }

    fun setKind(kind: String) {
        if (_state.value.kind == kind) return
        _state.update { it.copy(kind = kind) }
        search()
    }

    fun setSeason(value: String) = _state.update { it.copy(season = value.filter(Char::isDigit)) }
    fun setEpisode(value: String) = _state.update { it.copy(episode = value.filter(Char::isDigit)) }

    fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        val q = _state.value.query.trim()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false, searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(600)
            _state.update { it.copy(searching = true, error = null) }
            runCatching { repo.searchProviders(q, _state.value.kind) }
                .onSuccess { list -> _state.update { it.copy(searching = false, searched = true, results = list) } }
                .onFailure { e -> _state.update { it.copy(searching = false, searched = true, error = e.userMessage()) } }
        }
    }

    fun choose(candidate: ProviderCandidateDto) {
        val s = _state.value
        val season = s.season.toIntOrNull()
        val episode = s.episode.toIntOrNull()
        if (s.needsEpisode && (season == null || episode == null)) {
            _state.update { it.copy(error = "Indica temporada y episodio para asignar un fichero de serie") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (s.isFile) {
                    repo.matchFile(
                        s.target.channelId!!, s.target.fileId!!,
                        MatchFileRequest(
                            provider = candidate.provider,
                            providerId = candidate.providerId,
                            kind = s.kind,
                            season = if (s.kind == LibraryKind.SERIES) season else null,
                            episode = if (s.kind == LibraryKind.SERIES) episode else null
                        )
                    )
                } else {
                    repo.identifyItem(
                        s.target.itemId!!,
                        IdentifyItemRequest(provider = candidate.provider, providerId = candidate.providerId, kind = s.kind)
                    )
                }
            }
                .onSuccess { _done.send(Unit) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.userMessage()) } }
        }
    }

    fun ignore() {
        val s = _state.value
        if (!s.isFile) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.ignoreFile(s.target.channelId!!, s.target.fileId!!) }
                .onSuccess { _done.send(Unit) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.userMessage()) } }
        }
    }
}
