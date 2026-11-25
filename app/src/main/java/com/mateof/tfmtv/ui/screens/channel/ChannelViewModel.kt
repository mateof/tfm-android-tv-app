package com.mateof.tfmtv.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.BreadcrumbDto
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.model.ChannelRefreshProgressDto
import com.mateof.tfmtv.data.repo.ChannelsRepository
import com.mateof.tfmtv.data.repo.Feature
import com.mateof.tfmtv.data.repo.MediaUrls
import com.mateof.tfmtv.data.repo.ServerFeatures
import com.mateof.tfmtv.data.repo.VideoRepository
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.media.PlayLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    ChannelTab.VIDEOS to SortOption.NEWEST
)

/** What a tab shows, and how to go on. */
data class Listing(
    val folders: List<ApiFileDto> = emptyList(),
    val videos: List<ApiFileDto> = emptyList(),
    val messages: List<ChannelMessageDto> = emptyList(),
    val loaded: Boolean = false,
    /** Next page number, or message id in the messages tab; null when there is no more. */
    val next: Long? = null,
    /** How many there are in all, when the server says. */
    val total: Long? = null
) {
    val isEmpty: Boolean get() = folders.isEmpty() && videos.isEmpty() && messages.isEmpty()
}

data class ChannelState(
    val tab: ChannelTab = ChannelTab.FOLDERS,
    val details: ChannelDto? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val search: String = "",
    val path: String = "/",
    val folderId: String? = null,
    val parentFolderId: String? = null,
    val parentPath: String? = null,
    val breadcrumbs: List<BreadcrumbDto> = emptyList(),
    val listings: Map<ChannelTab, Listing> = emptyMap(),
    val filesByMessage: Map<Long, ApiFileDto> = emptyMap(),
    /** Messages play without being indexed (`messages.content`). */
    val playsMessages: Boolean = false,
    val messageThumbnails: Boolean = false,
    val rescanning: Boolean = false,
    val rescanMessage: String? = null,
    val message: String? = null,
    val sorts: Map<ChannelTab, SortOption> = defaultSorts,
    /** Counts the moves between folders: each one takes the focus to the new contents. */
    val moved: Int = 0
) {
    val canGoUp: Boolean get() = parentFolderId != null || parentPath != null
    val sort: SortOption get() = sorts[tab] ?: SortOption.NEWEST
    val listing: Listing get() = listings[tab] ?: Listing()
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repo: VideoRepository,
    private val channels: ChannelsRepository,
    private val features: ServerFeatures,
    private val mediaUrls: MediaUrls,
    private val launcher: PlayLauncher
) : ViewModel() {

    private var channelId: Long = 0

    private val _state = MutableStateFlow(ChannelState())
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    private val _events = Channel<PlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The card to give the focus back to: the video played, or the folder just left. */
    var lastFocus: String? = null

    private var loadJob: Job? = null
    private var index: Deferred<Map<Long, ApiFileDto>>? = null

    fun start(id: Long) {
        if (channelId == id) return
        channelId = id
        load()
        viewModelScope.launch {
            runCatching { channels.details(id) }.onSuccess { d -> _state.update { it.copy(details = d) } }
        }
        viewModelScope.launch {
            val plays = features.has(Feature.MESSAGE_CONTENT)
            _state.update { it.copy(playsMessages = plays, messageThumbnails = features.known(Feature.THUMBNAILS)) }
        }
    }

    fun selectTab(tab: ChannelTab) {
        if (_state.value.tab == tab) return
        lastFocus = null
        _state.update { it.copy(tab = tab, error = null, search = "") }
        // Another search left the list narrowed down.
        if (!_state.value.listing.loaded || searchedIn == tab) load() else _state.update { it.copy(loading = false) }
    }

    fun setSort(option: SortOption) {
        val tab = _state.value.tab
        if (_state.value.sort == option) return
        _state.update { it.copy(sorts = it.sorts + (tab to option)) }
        load()
    }

    private var searchJob: Job? = null
    private var searchedIn: ChannelTab? = null

    fun setSearch(value: String) {
        _state.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            lastFocus = null
            load()
        }
    }

    fun openFolder(folder: ApiFileDto) {
        // By id: the `path` an item carries is its *containing* folder, so using
        // it would always walk back to the level above.
        _state.update { it.copy(folderId = folder.id, path = "", search = "", moved = it.moved + 1) }
        lastFocus = null
        load()
    }

    fun up() {
        val state = _state.value
        if (!state.canGoUp) return
        lastFocus = state.folderId?.let { "d-$it" }
        _state.update { it.copy(folderId = state.parentFolderId, path = state.parentPath ?: "/", search = "", moved = it.moved + 1) }
        load()
    }

    /** The first page of the tab. */
    fun load() {
        loadJob?.cancel()
        val tab = _state.value.tab
        searchedIn = if (_state.value.search.isBlank()) null else tab
        _state.update { it.copy(loading = true, loadingMore = false, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { page(tab, first = true) }
                .onSuccess { listing -> _state.update { it.copy(loading = false, listings = it.listings + (tab to listing)) } }
                .onFailure { e -> fail(e) }
        }
    }

    /** The list has been scrolled close to its end. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.listing.next == null) return
        val tab = s.tab
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            runCatching { page(tab, first = false) }
                .onSuccess { listing -> _state.update { it.copy(loadingMore = false, listings = it.listings + (tab to listing)) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Keep what there is; asking again would fail in a loop.
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            message = e.userMessage(),
                            listings = it.listings + (tab to it.listing.copy(next = null))
                        )
                    }
                }
        }
    }

    private suspend fun page(tab: ChannelTab, first: Boolean): Listing {
        val s = _state.value
        val current = if (first) Listing() else s.listings[tab] ?: Listing()
        val text = s.search.trim()
        val sort = s.sorts[tab] ?: SortOption.NEWEST
        val number = if (first) 1 else (current.next ?: 1L).toInt()

        return when {
            tab == ChannelTab.MESSAGES -> messagePage(current, text, if (first) 0L else current.next ?: 0L)

            // A search from the folders covers the open one and everything below.
            tab == ChannelTab.VIDEOS || text.isNotBlank() -> {
                val paged = repo.videos(
                    channelId,
                    text = text,
                    path = if (tab == ChannelTab.FOLDERS) s.path.ifBlank { null } else null,
                    sortBy = sort.field.query,
                    sortDescending = sort.descending,
                    page = number
                )
                current.copy(
                    loaded = true,
                    videos = (current.videos + paged.items).distinctBy { it.id },
                    next = if (paged.page?.hasNext == true) number + 1L else null,
                    total = paged.page?.totalItems
                )
            }

            else -> {
                val paged = repo.browse(
                    channelId,
                    path = s.path.ifBlank { null },
                    folderId = s.folderId,
                    sortBy = sort.field.query,
                    sortDescending = sort.descending,
                    page = number
                )
                val contents = paged.items
                if (first) _state.update {
                    it.copy(
                        path = contents.currentPath ?: "/",
                        folderId = contents.currentFolderId,
                        parentFolderId = contents.parentFolderId,
                        parentPath = contents.parentPath,
                        breadcrumbs = contents.breadcrumbs
                    )
                }
                current.copy(
                    loaded = true,
                    folders = (current.folders + contents.items.filter { !it.isFile }).distinctBy { it.id },
                    videos = (current.videos + contents.items.filter { it.isFile }).distinctBy { it.id },
                    next = if (paged.page?.hasNext == true) number + 1L else null,
                    total = paged.page?.totalItems
                )
            }
        }
    }

    private suspend fun messagePage(current: Listing, text: String, from: Long): Listing {
        // Only to know the progress and the file of each message, so it does not hold the list back.
        if (index == null) {
            index = viewModelScope.async {
                repo.videoIndex(channelId).also { map -> _state.update { it.copy(filesByMessage = map) } }
            }
        }
        var next: Long? = from
        var total = current.total
        val found = mutableListOf<ChannelMessageDto>()
        // On an old server a page of messages may not have a single video.
        for (attempt in 0 until MESSAGE_PAGES) {
            val page = repo.videoMessages(channelId, text, next ?: break)
            found += page.items
            next = page.next
            total = page.total?.toLong() ?: total
            if (next == null || found.size >= MESSAGES_WANTED) break
        }
        return current.copy(
            loaded = true,
            messages = (current.messages + found).distinctBy { it.id },
            next = next,
            total = total
        )
    }

    fun toggleFavorite() {
        val channel = _state.value.details ?: return
        val favorite = !channel.isFavorite
        _state.update { it.copy(details = channel.copy(isFavorite = favorite)) }
        viewModelScope.launch {
            runCatching { channels.setFavorite(channelId, favorite) }
                .onSuccess { say(if (favorite) "Añadido a favoritos" else "Quitado de favoritos") }
                .onFailure { e ->
                    _state.update { it.copy(details = channel) }
                    say("No se pudo: ${e.userMessage()}")
                }
        }
    }

    /**
     * Asks the server to index what was posted since the last scan, waits for it
     * and reloads. The scan runs on the server, so leaving the screen does not
     * stop it; asking again while it runs does.
     */
    fun rescan() {
        if (_state.value.rescanning) {
            cancelRescan()
            return
        }
        val id = channelId
        _state.update { it.copy(rescanning = true, rescanMessage = "Buscando ficheros nuevos en Telegram…") }
        viewModelScope.launch {
            val outcome = runCatching {
                val started = channels.startRefresh(id)
                if (!started) {
                    _state.update { it.copy(rescanMessage = "Ya había un escaneo en marcha; esperando a que termine…") }
                }
                val detailed = features.has(Feature.REFRESH_PROGRESS)
                var waited = 0L
                do {
                    delay(if (detailed) RESCAN_DETAIL_MS else RESCAN_POLL_MS)
                    waited += if (detailed) RESCAN_DETAIL_MS else RESCAN_POLL_MS
                    if (detailed) channels.refreshProgress(id)?.let { p ->
                        _state.update { it.copy(rescanMessage = progressText(p)) }
                    }
                } while (channels.refreshRunning(id) && waited < RESCAN_MAX_WAIT_MS)
                if (waited >= RESCAN_MAX_WAIT_MS) null else channels.refreshResult(id)
            }
            if (id != channelId) return@launch
            val text = outcome.fold(
                onSuccess = { result ->
                    when {
                        result == null -> "El escaneo sigue en el servidor. Vuelve a cargar dentro de un rato."
                        result.canceled -> "Escaneo cancelado. ${plural(result.total, "fichero nuevo", "ficheros nuevos")}."
                        result.failed -> "El escaneo falló a mitad; se añadieron ${result.total} ficheros." +
                            (result.error?.let { " ($it)" } ?: "")
                        result.total == 0 -> "No hay ficheros nuevos."
                        else -> "${plural(result.total, "fichero nuevo", "ficheros nuevos")} (${plural(result.videos, "vídeo", "vídeos")})."
                    }
                },
                onFailure = { e -> "No se pudo escanear: ${e.userMessage()}" }
            )
            index = null
            _state.update {
                it.copy(
                    rescanning = false,
                    rescanMessage = null,
                    message = text,
                    // The other tabs load again when they are opened.
                    listings = if (outcome.isSuccess) it.listings.filterKeys { tab -> tab == it.tab } else it.listings
                )
            }
            if (outcome.isSuccess) load()
        }
    }

    private fun cancelRescan() {
        if (!features.known(Feature.REFRESH_PROGRESS)) return
        _state.update { it.copy(rescanMessage = "Cancelando…") }
        viewModelScope.launch {
            runCatching { channels.cancelRefresh(channelId) }
                .onFailure { e -> say("No se pudo cancelar: ${e.userMessage()}") }
        }
    }

    private fun progressText(p: ChannelRefreshProgressDto): String = when {
        p.cancelRequested -> "Cancelando…"
        p.phase == "indexing" -> "Añadiendo ${p.indexed} de ${p.newFiles} ficheros nuevos…"
        p.filters.any { it.waitUntil != null } -> "Telegram pide esperar un momento; el escaneo sigue solo…"
        else -> "Buscando en Telegram: " + p.filters.filter { !it.pending }.joinToString(", ") { f ->
            "${typeName(f.type)} ${f.fetched}"
        }
    }

    private fun typeName(type: String) = when (type) {
        "documents" -> "documentos"
        "audio" -> "audio"
        "video" -> "vídeo"
        "photos" -> "fotos"
        else -> type
    }

    /** Resumes from the server-side position when the internal player is used. */
    fun play(file: ApiFileDto, startMs: Long? = null) {
        viewModelScope.launch {
            _events.send(launcher.resolve(file, channelId, file.name, startMs = startMs))
        }
    }

    /**
     * An indexed message plays as its file, with its progress. The others play
     * straight from the message when the server can, and are looked for by name
     * when it cannot.
     */
    fun play(message: ChannelMessageDto) {
        fileOf(message)?.let { return play(it) }
        viewModelScope.launch {
            index?.await()?.get(message.id)?.let { return@launch play(it) }
            if (_state.value.playsMessages) {
                play(unindexed(message))
                return@launch
            }
            val file = runCatching { repo.resolveMessage(channelId, message) }.getOrNull()
            if (file == null) say("El vídeo del mensaje no está indexado en el canal")
            else play(file)
        }
    }

    /** Something that can be sent to a television, or null if it is not indexed and the server is old. */
    fun castable(message: ChannelMessageDto): ApiFileDto? =
        fileOf(message) ?: if (_state.value.playsMessages) unindexed(message) else null

    /** No id: there is no file on the server to keep the progress of. */
    private fun unindexed(message: ChannelMessageDto) = ApiFileDto(
        id = "",
        name = message.displayName(),
        isFile = true,
        size = message.fileSize,
        messageId = message.id,
        streamUrl = mediaUrls.messageContent(channelId, message.id)
    )

    fun fileOf(message: ChannelMessageDto): ApiFileDto? = _state.value.filesByMessage[message.id]

    fun say(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private fun fail(e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) return
        _state.update { it.copy(loading = false, error = e.userMessage()) }
    }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    private companion object {
        const val RESCAN_POLL_MS = 3_000L
        const val RESCAN_DETAIL_MS = 1_500L
        const val RESCAN_MAX_WAIT_MS = 15 * 60_000L
        const val MESSAGE_PAGES = 5
        const val MESSAGES_WANTED = 12
    }
}

fun ChannelMessageDto.displayName(): String =
    fileName?.takeIf { it.isNotBlank() } ?: text?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Mensaje $id"
