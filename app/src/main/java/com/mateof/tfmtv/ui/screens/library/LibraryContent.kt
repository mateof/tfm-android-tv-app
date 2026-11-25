package com.mateof.tfmtv.ui.screens.library

import com.mateof.tfmtv.ui.components.listRowBorder
import com.mateof.tfmtv.ui.components.listRowColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Radar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryFileStatus
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.AmbientBackdrop
import com.mateof.tfmtv.ui.components.ContentEnd
import com.mateof.tfmtv.ui.components.ContentStart
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.IconPill
import com.mateof.tfmtv.ui.components.MessageBar
import com.mateof.tfmtv.ui.components.OptionPicker
import com.mateof.tfmtv.ui.components.PickerOption
import com.mateof.tfmtv.ui.components.PosterTile
import com.mateof.tfmtv.ui.components.ProgressBar
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.components.SkeletonGrid
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.WideTile
import com.mateof.tfmtv.ui.components.entryFocus
import com.mateof.tfmtv.ui.components.rememberFocusReturn
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.returnFocus
import com.mateof.tfmtv.ui.components.tapClick
import com.mateof.tfmtv.ui.components.thumbnailOf
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.palette

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
    val urls = rememberMediaUrls()
    var menu by remember { mutableStateOf<LibraryMenuTarget?>(null) }
    var ambient by remember { mutableStateOf<String?>(null) }

    // Coming back from a detail, the player or the identify screen: progress may
    // have changed, so reload, but keep whatever is on screen.
    LaunchedEffect(Unit) { vm.refresh() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> vm.say(event.message)
                PlayEvent.Handed -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(ambient)
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = ContentStart, end = ContentEnd, top = 22.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryTab.entries.forEach { tab ->
                    TabChip(
                        label = tab.label,
                        selected = state.tab == tab,
                        onClick = {
                            ambient = null
                            vm.selectTab(tab)
                        },
                        // With a card to come back to, the focus goes there instead.
                        modifier = Modifier.entryFocus(state.tab == tab && vm.position(tab).key == null)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconPill(
                    icon = if (state.scanning) Icons.Outlined.Close else Icons.Outlined.Radar,
                    label = if (state.scanning) "Cancelar escaneo" else "Reescanear",
                    selected = state.scanning,
                    onClick = vm::scan
                )
                SearchField(
                    value = state.search,
                    onValueChange = vm::setSearch,
                    label = "Buscar título",
                    modifier = Modifier.width(200.dp)
                )
            }
            state.scanMessage?.let { text ->
                Row(
                    Modifier.padding(start = ContentStart, end = ContentEnd, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    state.scanProgress?.let { ProgressBar(it, Modifier.width(220.dp)) }
                }
            }
            if ((state.tab == LibraryTab.MOVIES || state.tab == LibraryTab.SERIES) && state.tab in state.loaded) {
                FilterBar(state = state, onChange = vm::setFilters)
            }

            Box(Modifier.fillMaxSize()) {
                val tab = state.tab
                when {
                    state.loading -> when (tab) {
                        LibraryTab.CONTINUE, LibraryTab.HISTORY -> SkeletonGrid(columns = 4, ratio = 1.3f)
                        LibraryTab.REVIEW -> SkeletonGrid(columns = 1, ratio = 14f, rows = 5)
                        else -> SkeletonGrid(columns = 6, ratio = 0.56f, rows = 2)
                    }
                    state.unavailable != null -> EmptyState(state.unavailable!!)
                    state.error != null -> ErrorState(state.error!!, onRetry = vm::refresh)
                    else -> when (tab) {
                        LibraryTab.CONTINUE, LibraryTab.HISTORY -> FileGrid(
                            files = if (tab == LibraryTab.CONTINUE) state.visibleContinue else state.visibleHistory,
                            tab = tab,
                            search = state.search,
                            position = vm.position(tab),
                            onScroll = { index, offset -> vm.saveScroll(tab, index, offset) },
                            onFocused = { ambient = urls.libraryImage(it.item?.posterUrl, "w92") },
                            onPlay = { file ->
                                vm.saveFocus(tab, fileKey(file))
                                vm.play(file)
                            },
                            onMenu = { file ->
                                vm.saveFocus(tab, fileKey(file))
                                menu = LibraryMenuTarget.File(file)
                            }
                        )

                        LibraryTab.MOVIES, LibraryTab.SERIES -> ItemGrid(
                            items = state.visibleItems,
                            tab = tab,
                            search = state.search,
                            filters = state.itemFilters,
                            position = vm.position(tab),
                            onScroll = { index, offset -> vm.saveScroll(tab, index, offset) },
                            onFocused = { ambient = urls.libraryImage(it.posterUrl, "w92") },
                            onOpen = { id ->
                                vm.saveFocus(tab, id)
                                onOpenItem(id)
                            },
                            onMenu = { item ->
                                vm.saveFocus(tab, item.id)
                                menu = LibraryMenuTarget.Item(item)
                            }
                        )

                        LibraryTab.REVIEW -> ReviewList(
                            files = state.visibleReview,
                            search = state.search,
                            position = vm.position(tab),
                            onScroll = { index, offset -> vm.saveScroll(tab, index, offset) },
                            onIdentify = { file ->
                                vm.saveFocus(tab, fileKey(file))
                                onIdentify(file.identifyTarget())
                            }
                        )
                    }
                }
            }
        }
        MessageBar(state.message, vm::messageShown)
    }

    menu?.let { target ->
        LibraryMenu(
            target = target,
            onDismiss = { menu = null },
            onPlay = { file, start -> vm.play(file, start) },
            onOpen = onOpenItem,
            onCast = { file ->
                castVm.open(CastTarget(file.file, file.channelId, file.displayTitle(), file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L))
            },
            onIdentify = onIdentify,
            onFileWatched = vm::setWatched,
            onItemWatched = vm::setWatched,
            onForget = vm::forget
        )
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

/** Files half way or already seen. */
@Composable
private fun FileGrid(
    files: List<LibraryFileDto>,
    tab: LibraryTab,
    search: String,
    position: TabPosition,
    onScroll: (Int, Int) -> Unit,
    onFocused: (LibraryFileDto) -> Unit,
    onPlay: (LibraryFileDto) -> Unit,
    onMenu: (LibraryFileDto) -> Unit
) {
    if (files.isEmpty()) {
        EmptyState(
            when {
                search.isNotBlank() -> "Nada coincide con «$search»."
                tab == LibraryTab.HISTORY -> "Todavía no hay nada visto."
                else -> "Nada a medias. Lo que dejes empezado aparecerá aquí."
            }
        )
        return
    }
    val urls = rememberMediaUrls()
    val gridState = rememberTabGridState(tab, position, onScroll)
    val back = rememberFocusReturn(position.key, tab)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        state = gridState,
        contentPadding = PaddingValues(start = ContentStart, end = ContentEnd, top = 8.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(files, key = { fileKey(it) }) { file ->
            val item = file.item
            val subtitle = listOfNotNull(
                if (item?.kind == LibraryKind.SERIES && file.episode != null) "T${file.season ?: 1} E${file.episode}"
                else item?.year?.toString() ?: file.sizeText.takeIf { item == null },
                if (tab == LibraryTab.HISTORY) Format.day(file.watch?.lastPlayedAt).takeIf { it.isNotBlank() }
                else file.watch?.let { Format.remaining(it.positionMs, it.durationMs) }
            ).joinToString(" · ")
            WideTile(
                title = item?.title ?: file.fileName,
                subtitle = subtitle,
                imageUrl = urls.libraryImage(item?.backdropUrl, "w500") ?: thumbnailOf(file.file),
                progress = file.watch?.takeIf { it.inProgress }?.progress?.toFloat(),
                completed = file.watch?.completed == true,
                placeholder = if (item?.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onPlay(file) },
                onLongClick = { onMenu(file) },
                onFocused = { onFocused(file) },
                modifier = Modifier.returnFocus(back, fileKey(file))
            )
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
    onFocused: (LibraryItemDto) -> Unit,
    onOpen: (String) -> Unit,
    onMenu: (LibraryItemDto) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(
            when {
                search.isNotBlank() -> "Nada coincide con «$search»."
                filters.narrowed -> "Nada coincide con los filtros elegidos."
                tab == LibraryTab.MOVIES -> "Todavía no hay películas identificadas. Lanza un escaneo con el botón de arriba."
                else -> "Todavía no hay series identificadas. Lanza un escaneo con el botón de arriba."
            }
        )
        return
    }
    val urls = rememberMediaUrls()
    val gridState = rememberTabGridState(tab, position, onScroll, contentKey = filters)
    val back = rememberFocusReturn(position.key, tab)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        state = gridState,
        contentPadding = PaddingValues(start = ContentStart, end = ContentEnd, top = 8.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val series = item.kind == LibraryKind.SERIES
            val subtitle = if (series && item.watch.episodesTotal > 0) {
                "${item.watch.episodesWatched} de ${item.watch.episodesTotal} vistos"
            } else item.year?.toString()
            PosterTile(
                title = item.title,
                subtitle = subtitle,
                imageUrl = urls.libraryImage(item.posterUrl, "w342"),
                progress = if (item.watch.inProgress) item.watch.progress.toFloat() else null,
                completed = item.watch.completed,
                rating = item.rating,
                placeholder = if (series) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onOpen(item.id) },
                onLongClick = { onMenu(item) },
                onFocused = { onFocused(item) },
                modifier = Modifier.returnFocus(back, item.id)
            )
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
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = ContentStart, end = ContentEnd, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabChip(label = filters.sort.label, selected = false, opens = true, onClick = { open = FilterPicker.SORT })
        TabChip(
            label = if (filters.watch == WatchFilter.ALL) "Vistas y sin ver" else filters.watch.label,
            selected = filters.watch != WatchFilter.ALL,
            opens = true,
            onClick = { open = FilterPicker.WATCH }
        )
        if (state.genres.isNotEmpty() || filters.genre != null) {
            TabChip(
                label = filters.genre ?: "Todos los géneros",
                selected = filters.genre != null,
                opens = true,
                onClick = { open = FilterPicker.GENRE }
            )
        }
        if (filters.narrowed) {
            IconPill(Icons.Outlined.FilterAltOff, "Quitar filtros", {
                onChange(filters.copy(watch = WatchFilter.ALL, genre = null))
            })
        }
        Text(
            text = state.visibleItems.size.let { if (it == 1) "1 título" else "$it títulos" },
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceMuted,
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
    onIdentify: (LibraryFileDto) -> Unit
) {
    if (files.isEmpty()) {
        EmptyState(
            if (search.isBlank()) "No hay ficheros pendientes de revisar."
            else "Nada coincide con «$search»."
        )
        return
    }
    val listState = rememberTabListState(LibraryTab.REVIEW, position, onScroll)
    val back = rememberFocusReturn(position.key, LibraryTab.REVIEW)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = ContentStart, end = ContentEnd, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { fileKey(it) }) { file ->
            ReviewRow(file = file, modifier = Modifier.returnFocus(back, fileKey(file))) { onIdentify(file) }
        }
    }
}

@Composable
private fun ReviewRow(file: LibraryFileDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        border = listRowBorder(),
        colors = listRowColors()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Text(text = guess, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
