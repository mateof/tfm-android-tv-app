package com.mateof.tfmtv.ui.screens.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.model.SearchChannelGroupDto
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.LibraryRepository
import com.mateof.tfmtv.data.repo.SearchRepository
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.media.PlayLauncher
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.ActionMenu
import com.mateof.tfmtv.ui.components.ChannelTile
import com.mateof.tfmtv.ui.components.ContentEnd
import com.mateof.tfmtv.ui.components.ContentStart
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.IconPill
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.MediaRow
import com.mateof.tfmtv.ui.components.MenuAction
import com.mateof.tfmtv.ui.components.MessageBar
import com.mateof.tfmtv.ui.components.PivotRows
import com.mateof.tfmtv.ui.components.PosterTile
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.WideTile
import com.mateof.tfmtv.ui.components.entryFocus
import com.mateof.tfmtv.ui.components.rememberFocusReturn
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.returnFocus
import com.mateof.tfmtv.ui.components.subtitle
import com.mateof.tfmtv.ui.components.thumbnailOf
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val searching: Boolean = false,
    /** What the results on screen answer to; blank before the first search. */
    val searched: String = "",
    val titles: List<LibraryItemDto> = emptyList(),
    val groups: List<SearchChannelGroupDto> = emptyList(),
    val truncated: Boolean = false,
    /** An old server cannot look inside every channel at once. */
    val noFiles: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val search: SearchRepository,
    private val launcher: PlayLauncher,
    private val prefs: ServerPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val recent: StateFlow<List<String>> =
        prefs.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    var lastFocus: String? = null

    private var job: Job? = null

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        run(debounce = true)
    }

    fun run(debounce: Boolean = false) {
        job?.cancel()
        val text = _state.value.query.trim()
        if (text.length < MIN_LENGTH) {
            _state.update { SearchState(query = it.query) }
            return
        }
        job = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            lastFocus = null
            _state.update { it.copy(searching = true) }
            supervisorScope {
                // The library may be off; that only leaves its row out.
                val titles = async { runCatching { library.search(text) }.getOrDefault(emptyList()) }
                val files = async { runCatching { search.videos(text) } }
                val found = files.await()
                _state.update {
                    it.copy(
                        searching = false,
                        searched = text,
                        titles = titles.await(),
                        groups = found.getOrNull()?.channels.orEmpty(),
                        truncated = found.getOrNull()?.truncated == true,
                        noFiles = found.isSuccess && found.getOrNull() == null,
                        message = found.exceptionOrNull()?.userMessage()
                    )
                }
            }
        }
    }

    /** Only what led somewhere is worth remembering. */
    fun remember() {
        val text = _state.value.searched
        viewModelScope.launch { prefs.rememberSearch(text) }
    }

    fun forgetSearches() {
        viewModelScope.launch { prefs.forgetSearches() }
    }

    fun play(file: ApiFileDto, channelId: Long) {
        remember()
        viewModelScope.launch { _events.send(launcher.resolve(file, channelId, file.name, startMs = null)) }
    }

    fun say(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object {
        const val MIN_LENGTH = 2
        const val DEBOUNCE_MS = 500L
    }
}

private class FileMenu(val file: ApiFileDto, val channelId: Long, val channelName: String)

@Composable
fun SearchContent(
    channels: List<ChannelDto>,
    onChannel: (Long, String) -> Unit,
    onChannelMenu: (ChannelDto) -> Unit,
    onOpenItem: (String) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit
) {
    val vm: SearchViewModel = hiltViewModel()
    val castVm: CastViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val urls = rememberMediaUrls()
    var menu by remember { mutableStateOf<FileMenu?>(null) }
    val back = rememberFocusReturn(vm.lastFocus)

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> vm.say(event.message)
                PlayEvent.Handed -> Unit
            }
        }
    }

    val matching = remember(channels, state.searched) {
        if (state.searched.isBlank()) emptyList()
        else channels.filter { it.name.orEmpty().contains(state.searched, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(start = ContentStart, end = ContentEnd, top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SearchField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    label = "Buscar películas, series, vídeos o canales",
                    onSearch = { vm.run() },
                    modifier = Modifier.width(520.dp).entryFocus()
                )
                if (state.searched.isNotBlank() && !state.searching) {
                    val videos = state.groups.sumOf { it.files.size }
                    Text(
                        listOfNotNull(
                            state.titles.size.takeIf { it > 0 }?.let { if (it == 1) "1 título" else "$it títulos" },
                            videos.takeIf { it > 0 }?.let { (if (it == 1) "1 vídeo" else "$it vídeos") + if (state.truncated) " (hay más)" else "" }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
            }
            if (state.searched.isBlank() && recent.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = ContentStart, end = ContentEnd, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recent.forEach { text ->
                        TabChip(text, selected = false, icon = Icons.Outlined.History, onClick = {
                            vm.setQuery(text)
                            vm.run()
                        })
                    }
                    IconPill(Icons.Outlined.DeleteSweep, "Borrar historial", vm::forgetSearches)
                }
            }

            when {
                state.searching && state.titles.isEmpty() && state.groups.isEmpty() -> Loading()
                state.searched.isBlank() -> EmptyState(
                    "Busca a la vez en la biblioteca, en los ficheros de todos los canales y en los nombres de los canales.",
                    icon = Icons.Outlined.Search
                )
                state.titles.isEmpty() && state.groups.isEmpty() && matching.isEmpty() -> EmptyState(
                    "Nada coincide con «${state.searched}»." +
                        if (state.noFiles) " El servidor es antiguo y no puede buscar ficheros en todos los canales: actualízalo." else "",
                    icon = Icons.Outlined.Search
                )
                else -> PivotRows(top = 40.dp) { rowSpec ->
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 60.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.titles.isNotEmpty()) item(key = "titles") {
                            MediaRow("Biblioteca", spec = rowSpec) {
                                items(state.titles, key = { it.id }) { item ->
                                    PosterTile(
                                        title = item.title,
                                        subtitle = item.year?.toString(),
                                        imageUrl = urls.libraryImage(item.posterUrl, "w342"),
                                        progress = if (item.watch.inProgress) item.watch.progress.toFloat() else null,
                                        completed = item.watch.completed,
                                        rating = item.rating,
                                        placeholder = if (item.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie,
                                        onClick = {
                                            vm.remember()
                                            vm.lastFocus = item.id
                                            onOpenItem(item.id)
                                        },
                                        modifier = Modifier.width(118.dp).returnFocus(back, item.id)
                                    )
                                }
                            }
                        }
                        if (matching.isNotEmpty()) item(key = "channels") {
                            MediaRow("Canales", spec = rowSpec) {
                                items(matching, key = { it.id }) { channel ->
                                    ChannelTile(
                                        channel = channel,
                                        onClick = {
                                            vm.remember()
                                            vm.lastFocus = "channel-${channel.id}"
                                            onChannel(channel.id, channel.name.orEmpty())
                                        },
                                        onLongClick = { onChannelMenu(channel) },
                                        modifier = Modifier.width(250.dp).returnFocus(back, "channel-${channel.id}")
                                    )
                                }
                            }
                        }
                        state.groups.forEach { group ->
                            val id = group.channelId.toLongOrNull() ?: return@forEach
                            item(key = "group-$id") {
                                MediaRow(group.channelName, detail = "${group.files.size}", spec = rowSpec) {
                                    items(group.files, key = { it.id }) { file ->
                                        WideTile(
                                            title = file.name,
                                            subtitle = file.subtitle(),
                                            imageUrl = thumbnailOf(file),
                                            onClick = {
                                                vm.lastFocus = "$id-${file.id}"
                                                vm.play(file, id)
                                            },
                                            onLongClick = { menu = FileMenu(file, id, group.channelName) },
                                            modifier = Modifier.width(190.dp).returnFocus(back, "$id-${file.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        MessageBar(state.message, vm::messageShown)
    }

    menu?.let { target ->
        ActionMenu(
            title = target.file.name,
            subtitle = target.channelName,
            onDismiss = { menu = null },
            actions = listOf(
                MenuAction("Reproducir", Icons.Outlined.PlayArrow) { vm.play(target.file, target.channelId) },
                MenuAction("Abrir el canal", Icons.Outlined.Folder) { onChannel(target.channelId, target.channelName) },
                MenuAction("Enviar a la tele", Icons.Outlined.Cast) {
                    castVm.open(CastTarget(target.file, target.channelId, target.file.name))
                }
            )
        )
    }
    CastSheet(castVm, onOpenPanel = onOpenCastPanel)
}
