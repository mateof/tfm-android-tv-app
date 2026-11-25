package com.mateof.tfmtv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryFileStatus
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.model.LibraryScanStateDto
import com.mateof.tfmtv.data.repo.LibraryActions
import com.mateof.tfmtv.data.repo.LibraryErrors
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.LibraryRepository
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.media.PlayLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    CONTINUE("Continuar"),
    MOVIES("Películas"),
    SERIES("Series"),
    HISTORY("Vistos"),
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
    val historyFiles: List<LibraryFileDto> = emptyList(),
    val loaded: Set<LibraryTab> = emptySet(),
    val movieFilters: ItemFilters = ItemFilters(),
    val seriesFilters: ItemFilters = ItemFilters(),
    val scanning: Boolean = false,
    val scanMessage: String? = null,
    /** 0..1 while the scan knows how much there is to do. */
    val scanProgress: Float? = null,
    val message: String? = null
) {
    private val query: String get() = search.trim()

    val visibleContinue: List<LibraryFileDto>
        get() = continueFiles.filter { query.isBlank() || it.displayTitle().contains(query, ignoreCase = true) }

    val visibleHistory: List<LibraryFileDto>
        get() = historyFiles.filter { query.isBlank() || it.displayTitle().contains(query, ignoreCase = true) }

    private val tabItems: List<LibraryItemDto> get() = if (tab == LibraryTab.MOVIES) movies else series

    val itemFilters: ItemFilters get() = if (tab == LibraryTab.SERIES) seriesFilters else movieFilters

    val visibleItems: List<LibraryItemDto>
        get() = itemFilters.apply(tabItems).filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true)
                || it.originalTitle?.contains(query, ignoreCase = true) == true
        }

    /** Genres present in the tab, most common first, with how many titles have each. */
    val genres: List<Pair<String, Int>>
        get() = tabItems.flatMap { it.genres }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

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

/** What the identify screen needs to start from this file. */
fun LibraryFileDto.identifyTarget() = IdentifyTarget(
    channelId = channelId,
    fileId = fileId,
    itemId = itemId,
    kind = item?.kind ?: kind ?: if (parsed.isSeries) LibraryKind.SERIES else LibraryKind.MOVIE,
    query = item?.title ?: parsed.title.ifBlank { fileName },
    season = season ?: parsed.season,
    episode = episode ?: parsed.episode
)

/**
 * A scan first reads the index of every channel and then identifies what is
 * new; a server that does not tell them apart only reports the channels.
 */
fun scanText(s: LibraryScanStateDto): String = buildString {
    if (s.filesToProcess > 0) {
        append("Identificando ${s.filesProcessed} de ${s.filesToProcess} ficheros")
    } else {
        append("Leyendo los canales")
        if (s.channelsTotal > 0) append(" (${s.channelsScanned} de ${s.channelsTotal})")
        s.currentChannel?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
    if (s.filesNew > 0) append(" · ${s.filesNew} nuevos")
}

fun scanFraction(s: LibraryScanStateDto): Float? =
    if (s.filesToProcess > 0) (s.filesProcessed / s.filesToProcess.toFloat()).coerceIn(0f, 1f) else null

enum class LibrarySort(val label: String) {
    ADDED("Últimas añadidas"),
    TITLE("Título (A-Z)"),
    YEAR("Año (más recientes)"),
    RATING("Mejor valoradas"),
    LAST_PLAYED("Vistas recientemente")
}

enum class WatchFilter(val label: String) {
    ALL("Todas"),
    UNWATCHED("Sin ver"),
    IN_PROGRESS("A medias"),
    WATCHED("Vistas")
}

/** How the movie or series grid is ordered and narrowed down. */
data class ItemFilters(
    val sort: LibrarySort = LibrarySort.ADDED,
    val watch: WatchFilter = WatchFilter.ALL,
    val genre: String? = null
) {
    val narrowed: Boolean get() = watch != WatchFilter.ALL || genre != null

    fun apply(items: List<LibraryItemDto>): List<LibraryItemDto> =
        items.filter { (genre == null || genre in it.genres) && it.matches(watch) }.sortedWith(comparator())

    private fun comparator(): Comparator<LibraryItemDto> {
        val byTitle = compareBy(TITLE_ORDER) { it: LibraryItemDto -> it.title }
        // Missing values always go to the end, whatever the direction.
        return when (sort) {
            LibrarySort.TITLE -> byTitle
            LibrarySort.ADDED -> compareBy<LibraryItemDto, String?>(nullsLast(reverseOrder())) { it.addedAt }
            LibrarySort.YEAR -> compareBy<LibraryItemDto, Int?>(nullsLast(reverseOrder())) { it.year }
            LibrarySort.RATING -> compareBy<LibraryItemDto, Double?>(nullsLast(reverseOrder())) { it.rating }
            LibrarySort.LAST_PLAYED -> compareBy<LibraryItemDto, String?>(nullsLast(reverseOrder())) { it.watch.lastPlayedAt }
        }.then(byTitle)
    }

    private companion object {
        val TITLE_ORDER: Comparator<String> =
            Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }.let { c -> Comparator { a, b -> c.compare(a, b) } }
    }
}

/** A series counts as started as soon as one episode has been watched. */
private fun LibraryItemDto.matches(filter: WatchFilter): Boolean {
    val started = watch.inProgress || watch.positionMs > 0 || watch.episodesWatched > 0
    return when (filter) {
        WatchFilter.ALL -> true
        WatchFilter.WATCHED -> watch.completed
        WatchFilter.IN_PROGRESS -> !watch.completed && started
        WatchFilter.UNWATCHED -> !watch.completed && !started
    }
}

/** Where a tab was left: what was on screen and which card the user left through. */
data class TabPosition(val index: Int = 0, val offset: Int = 0, val key: String? = null)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val actions: LibraryActions,
    private val launcher: PlayLauncher,
    private val prefs: ServerPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Scroll and focus per tab. The ViewModel outlives the screen while Home is
    // on the back stack, so opening a detail and coming back lands on the same
    // place instead of at the top of the grid.
    private val positions = mutableMapOf<LibraryTab, TabPosition>()

    init {
        // The chosen order is remembered between sessions; filters are not.
        viewModelScope.launch {
            val movies = savedSort(LibraryKind.MOVIE)
            val series = savedSort(LibraryKind.SERIES)
            _state.update {
                it.copy(
                    movieFilters = it.movieFilters.copy(sort = movies ?: it.movieFilters.sort),
                    seriesFilters = it.seriesFilters.copy(sort = series ?: it.seriesFilters.sort)
                )
            }
        }
    }

    private suspend fun savedSort(kind: String): LibrarySort? =
        prefs.librarySort(kind)?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }

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
     * Changes the order or filters of the visible movie/series tab. The list it
     * was scrolled through is gone, so the grid starts again from the top.
     */
    fun setFilters(filters: ItemFilters) {
        val tab = _state.value.tab
        if (tab != LibraryTab.MOVIES && tab != LibraryTab.SERIES) return
        val previous = _state.value.itemFilters
        positions[tab] = TabPosition()
        _state.update {
            if (tab == LibraryTab.MOVIES) it.copy(movieFilters = filters) else it.copy(seriesFilters = filters)
        }
        if (filters.sort != previous.sort) {
            val kind = if (tab == LibraryTab.MOVIES) LibraryKind.MOVIE else LibraryKind.SERIES
            viewModelScope.launch { prefs.saveLibrarySort(kind, filters.sort.name) }
        }
    }

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
                    LibraryTab.HISTORY -> {
                        val list = repo.history()
                        _state.update { it.copy(historyFiles = list) }
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

    /**
     * Has the server look through the indexed channels for new files and identify
     * them. Progress is polled; the scan itself keeps going if the screen is left.
     */
    fun scan() {
        if (_state.value.scanning) {
            cancelScan()
            return
        }
        _state.update { it.copy(scanning = true, scanProgress = null, scanMessage = "Escaneando la biblioteca…") }
        viewModelScope.launch {
            val outcome = runCatching {
                if (!repo.startScan()) {
                    _state.update { it.copy(scanMessage = "Ya había un escaneo en marcha; siguiéndolo…") }
                }
                var last = repo.scanState()
                while (last?.running == true) {
                    _state.update { it.copy(scanMessage = scanText(last!!), scanProgress = scanFraction(last!!)) }
                    delay(SCAN_POLL_MS)
                    last = repo.scanState()
                }
                last
            }
            val text = outcome.fold(
                onSuccess = { s ->
                    when {
                        s == null -> "Escaneo terminado."
                        s.cancelled -> "Escaneo cancelado. ${s.filesNew} ficheros nuevos."
                        // Not always the end of it: a provider out of quota is also told here.
                        s.error != null -> "Escaneo terminado con avisos: ${s.error}"
                        else -> "Escaneo terminado: ${s.filesNew} ficheros nuevos, ${s.matched} identificados, ${s.review + s.unmatched} por revisar."
                    }
                },
                onFailure = { e -> LibraryErrors.unavailableMessage(e) ?: "No se pudo escanear: ${e.userMessage()}" }
            )
            _state.update { it.copy(scanning = false, scanMessage = null, scanProgress = null, message = text, loaded = emptySet()) }
            if (outcome.isSuccess) refresh()
        }
    }

    private fun cancelScan() {
        _state.update { it.copy(scanMessage = "Cancelando el escaneo…") }
        viewModelScope.launch {
            runCatching { repo.cancelScan() }
                .onFailure { e -> _state.update { it.copy(message = "No se pudo cancelar: ${e.userMessage()}") } }
        }
    }

    fun setWatched(file: LibraryFileDto, watched: Boolean) = change { actions.setWatched(file, watched) }
    fun setWatched(item: LibraryItemDto, watched: Boolean) = change { actions.setWatched(item, watched) }
    fun forget(file: LibraryFileDto) = change { actions.forget(file) }

    private fun change(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            val result = block()
            _state.update {
                it.copy(
                    message = result.getOrElse { e -> e.message },
                    // The other tabs show it too, but there is no need to empty them.
                    loaded = if (result.isSuccess) setOf(it.tab) else it.loaded
                )
            }
            if (result.isSuccess) refresh()
        }
    }

    fun say(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }

    /** Continue watching: resumes where the server says it stopped. */
    fun play(file: LibraryFileDto, startMs: Long = file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L) {
        viewModelScope.launch {
            _events.send(launcher.resolve(file.file, file.channelId, file.displayTitle(), startMs))
        }
    }

    private companion object {
        const val SCAN_POLL_MS = 3_000L
    }
}
