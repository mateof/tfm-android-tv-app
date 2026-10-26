package com.mateof.tfmtv.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryFileStatus
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.OptionPicker
import com.mateof.tfmtv.ui.components.PickerOption
import com.mateof.tfmtv.ui.components.PosterCard
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.tapClick
import kotlinx.coroutines.delay

/** What the identify screen needs to know about the file (or item) being corrected. */
data class IdentifyTarget(
    val channelId: Long? = null,
    val fileId: String? = null,
    val itemId: String? = null,
    val kind: String = LibraryKind.MOVIE,
    val query: String = "",
    val season: Int? = null,
    val episode: Int? = null
)

@Composable
fun LibraryContent(
    onOpenItem: (String) -> Unit,
    onIdentify: (IdentifyTarget) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit
) {
    val vm: LibraryViewModel = hiltViewModel()
    val castVm: CastViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    // Coming back from a detail, the player or the identify screen: progress may
    // have changed, so reload, but keep whatever is on screen. Focus only goes
    // to the tabs on a fresh entry; when there is a card to come back to, that
    // card takes the focus (and a focused text field would pop the TV keyboard).
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val comingBack = vm.position(vm.state.value.tab).key != null
        vm.refresh()
        if (!comingBack) {
            delay(120)
            runCatching { tabFocus.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> message = event.message
                PlayEvent.Handed -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Biblioteca",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TabChip(
                label = if (state.scanning) "Escaneando…" else "Reescanear biblioteca",
                selected = state.scanning,
                onClick = vm::scan
            )
            SearchField(
                value = state.search,
                onValueChange = vm::setSearch,
                label = "Buscar título",
                modifier = Modifier.width(360.dp)
            )
        }
        state.scanMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.padding(start = 40.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                TabChip(
                    label = tab.label,
                    selected = state.tab == tab,
                    onClick = { vm.selectTab(tab) },
                    modifier = if (state.tab == tab) Modifier.focusRequester(tabFocus) else Modifier
                )
            }
        }

        if ((state.tab == LibraryTab.MOVIES || state.tab == LibraryTab.SERIES) && state.tab in state.loaded) {
            FilterBar(state = state, onChange = vm::setFilters)
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)
            )
        }

        Box(Modifier.fillMaxSize()) {
            val tab = state.tab
            when {
                state.loading -> Loading()
                state.unavailable != null -> EmptyState(state.unavailable!!)
                state.error != null -> ErrorState(state.error!!, onRetry = vm::refresh)
                else -> when (tab) {
                    LibraryTab.CONTINUE -> ContinueGrid(
                        files = state.visibleContinue,
                        search = state.search,
                        position = vm.position(tab),
                        onScroll = { index, offset -> vm.saveScroll(tab, index, offset) },
                        onPlay = { file ->
                            vm.saveFocus(tab, fileKey(file))
                            vm.play(file)
                        },
                        onCast = { file ->
                            val start = file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L
                            castVm.open(CastTarget(file.file, file.channelId, file.displayTitle(), start))
                        }
                    )

                    LibraryTab.MOVIES, LibraryTab.SERIES -> ItemGrid(
                        items = state.visibleItems,
                        tab = tab,
                        search = state.search,
                        filters = state.itemFilters,
                        position = vm.position(tab),
                        onScroll = { index, offset -> vm.saveScroll(tab, index, offset) },
                        onOpen = { id ->
                            vm.saveFocus(tab, id)
                            onOpenItem(id)
                        }
                    )

                    LibraryTab.REVIEW -> ReviewList(
                        files = state.visibleReview,
                        search = state.search,
                        position = vm.position(tab),
                        onScroll = { index, offset -> vm.saveScroll(tab, index, offset) },
                        onIdentify = { file, target ->
                            vm.saveFocus(tab, fileKey(file))
                            onIdentify(target)
                        }
                    )
                }
            }
        }
    }

    CastSheet(castVm, onOpenPanel = onOpenCastPanel)
}

private fun fileKey(file: LibraryFileDto) = "${file.channelId}-${file.fileId}"

/**
 * Grid that opens where the tab was left and reports every move, so leaving for
 * a detail screen and coming back does not send the user back to the top.
 */
@Composable
private fun rememberTabGridState(
    tab: LibraryTab,
    position: TabPosition,
    onScroll: (Int, Int) -> Unit,
    contentKey: Any? = null
): LazyGridState {
    // A new order or filter is a different list: start it from the top.
    val state = rememberSaveable(tab, contentKey, saver = LazyGridState.Saver) {
        LazyGridState(position.index, position.offset)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScroll(index, offset) }
    }
    return state
}

@Composable
private fun rememberTabListState(tab: LibraryTab, position: TabPosition, onScroll: (Int, Int) -> Unit): LazyListState {
    val state = rememberSaveable(tab, saver = LazyListState.Saver) {
        LazyListState(position.index, position.offset)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScroll(index, offset) }
    }
    return state
}

/**
 * Focus handling for the card the user left through: it keeps the requester
 * attached to that card and fires once, after the row has been laid out.
 */
private class FocusRestore(val key: String?) {
    val requester = FocusRequester()
    var done = false
}

@Composable
private fun rememberFocusRestore(tab: LibraryTab, position: TabPosition): FocusRestore =
    // Deliberately not rememberSaveable: navigating away and back must restore
    // the focus again, and a saved "already done" flag would swallow it.
    remember(tab, position.key) { FocusRestore(position.key) }

@Composable
private fun FocusRestore.RequestOnce(isTarget: Boolean) {
    if (!isTarget || done) return
    LaunchedEffect(Unit) {
        delay(80)   // the card has to exist before it can take focus
        runCatching { requester.requestFocus() }
        done = true
    }
}

@Composable
private fun ContinueGrid(
    files: List<LibraryFileDto>,
    search: String,
    position: TabPosition,
    onScroll: (Int, Int) -> Unit,
    onPlay: (LibraryFileDto) -> Unit,
    onCast: (LibraryFileDto) -> Unit
) {
    if (files.isEmpty()) {
        EmptyState(
            if (search.isBlank()) "Nada a medias. Lo que dejes empezado aparecerá aquí."
            else "Nada coincide con «$search»."
        )
        return
    }
    val urls = rememberMediaUrls()
    val gridState = rememberTabGridState(LibraryTab.CONTINUE, position, onScroll)
    val focus = rememberFocusRestore(LibraryTab.CONTINUE, position)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(files, key = { fileKey(it) }) { file ->
            val item = file.item
            val subtitle = when {
                item != null && item.kind == LibraryKind.SERIES && file.episode != null ->
                    "T${file.season ?: 1} · E${file.episode}"
                item != null -> item.year?.toString()
                else -> file.sizeText
            }
            val isTarget = fileKey(file) == focus.key
            PosterCard(
                title = item?.title ?: file.fileName,
                subtitle = subtitle,
                imageUrl = urls.absolute(item?.posterUrl),
                progress = file.watch?.progress?.toFloat(),
                completed = file.watch?.completed == true,
                placeholder = if (item?.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onPlay(file) },
                onLongClick = { onCast(file) },
                modifier = if (isTarget) Modifier.focusRequester(focus.requester) else Modifier
            )
            focus.RequestOnce(isTarget)
        }
    }
}

@Composable
private fun ItemGrid(
    items: List<LibraryItemDto>,
    tab: LibraryTab,
    search: String,
    filters: ItemFilters,
    position: TabPosition,
    onScroll: (Int, Int) -> Unit,
    onOpen: (String) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(
            when {
                search.isNotBlank() -> "Nada coincide con «$search»."
                filters.narrowed -> "Nada coincide con los filtros elegidos."
                tab == LibraryTab.MOVIES -> "Todavía no hay películas identificadas. Lanza un escaneo desde la web del servidor."
                else -> "Todavía no hay series identificadas. Lanza un escaneo desde la web del servidor."
            }
        )
        return
    }
    val urls = rememberMediaUrls()
    val gridState = rememberTabGridState(tab, position, onScroll, contentKey = filters)
    val focus = rememberFocusRestore(tab, position)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val series = item.kind == LibraryKind.SERIES
            val subtitle = if (series) {
                listOfNotNull(
                    item.year?.toString(),
                    "${item.watch.episodesWatched}/${item.watch.episodesTotal} episodios"
                ).joinToString(" · ")
            } else item.year?.toString()
            val isTarget = item.id == focus.key
            PosterCard(
                title = item.title,
                subtitle = subtitle,
                imageUrl = urls.absolute(item.posterUrl),
                progress = if (item.watch.inProgress) item.watch.progress.toFloat() else null,
                completed = item.watch.completed,
                placeholder = if (series) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onOpen(item.id) },
                modifier = if (isTarget) Modifier.focusRequester(focus.requester) else Modifier
            )
            focus.RequestOnce(isTarget)
        }
    }
}

private enum class FilterPicker { SORT, WATCH, GENRE }

/** Order, watch state and genre of the movie/series grid, like other media apps. */
@Composable
private fun FilterBar(state: LibraryState, onChange: (ItemFilters) -> Unit) {
    val filters = state.itemFilters
    var open by remember { mutableStateOf<FilterPicker?>(null) }
    Row(
        modifier = Modifier.padding(start = 40.dp, end = 40.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabChip(
            label = "Orden: ${filters.sort.label}",
            selected = false,
            onClick = { open = FilterPicker.SORT }
        )
        TabChip(
            label = "Mostrar: ${filters.watch.label}",
            selected = filters.watch != WatchFilter.ALL,
            onClick = { open = FilterPicker.WATCH }
        )
        if (state.genres.isNotEmpty() || filters.genre != null) {
            TabChip(
                label = filters.genre ?: "Todos los géneros",
                selected = filters.genre != null,
                onClick = { open = FilterPicker.GENRE }
            )
        }
        if (filters.narrowed) {
            TabChip(
                label = "Quitar filtros",
                selected = false,
                onClick = { onChange(filters.copy(watch = WatchFilter.ALL, genre = null)) }
            )
        }
        Text(
            text = state.visibleItems.size.let { if (it == 1) "1 título" else "$it títulos" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    when (open) {
        FilterPicker.SORT -> OptionPicker(
            title = "Ordenar por",
            options = LibrarySort.entries.map { PickerOption(it, it.label) },
            selected = filters.sort,
            onSelect = { onChange(filters.copy(sort = it)) },
            onDismiss = { open = null }
        )
        FilterPicker.WATCH -> OptionPicker(
            title = "Mostrar",
            options = WatchFilter.entries.map { PickerOption(it, it.label) },
            selected = filters.watch,
            onSelect = { onChange(filters.copy(watch = it)) },
            onDismiss = { open = null }
        )
        FilterPicker.GENRE -> OptionPicker(
            title = "Género",
            options = listOf(PickerOption<String?>(null, "Todos los géneros")) +
                state.genres.map { (genre, count) -> PickerOption<String?>(genre, genre, count.toString()) },
            selected = filters.genre,
            onSelect = { onChange(filters.copy(genre = it)) },
            onDismiss = { open = null }
        )
        null -> Unit
    }
}

@Composable
private fun ReviewList(
    files: List<LibraryFileDto>,
    search: String,
    position: TabPosition,
    onScroll: (Int, Int) -> Unit,
    onIdentify: (LibraryFileDto, IdentifyTarget) -> Unit
) {
    if (files.isEmpty()) {
        EmptyState(
            if (search.isBlank()) "No hay ficheros pendientes de revisar."
            else "Nada coincide con «$search»."
        )
        return
    }
    val listState = rememberTabListState(LibraryTab.REVIEW, position, onScroll)
    val focus = rememberFocusRestore(LibraryTab.REVIEW, position)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { fileKey(it) }) { file ->
            val isTarget = fileKey(file) == focus.key
            ReviewRow(
                file = file,
                modifier = if (isTarget) Modifier.focusRequester(focus.requester) else Modifier
            ) {
                onIdentify(
                    file,
                    IdentifyTarget(
                        channelId = file.channelId,
                        fileId = file.fileId,
                        itemId = file.itemId,
                        kind = file.item?.kind ?: file.kind ?: if (file.parsed.isSeries) LibraryKind.SERIES else LibraryKind.MOVIE,
                        query = file.item?.title ?: file.parsed.title.ifBlank { file.fileName },
                        season = file.season ?: file.parsed.season,
                        episode = file.episode ?: file.parsed.episode
                    )
                )
            }
            focus.RequestOnce(isTarget)
        }
    }
}

@Composable
private fun ReviewRow(file: LibraryFileDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val guess = buildString {
                append(if (file.status == LibraryFileStatus.REVIEW) "Dudoso" else "Sin identificar")
                if (file.parsed.title.isNotBlank()) {
                    append(" · Leído: ").append(file.parsed.title)
                    file.parsed.year?.let { append(" ($it)") }
                    if (file.parsed.episode != null) append(" T${file.parsed.season ?: 1} E${file.parsed.episode}")
                }
                file.item?.let { append(" · Propuesto: ").append(it.title); it.year?.let { y -> append(" ($y)") } }
            }
            Text(
                text = guess,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
