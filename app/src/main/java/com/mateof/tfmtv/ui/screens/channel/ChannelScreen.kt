package com.mateof.tfmtv.ui.screens.channel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.ActionMenu
import com.mateof.tfmtv.ui.components.ChannelAvatar
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.FocusReturn
import com.mateof.tfmtv.ui.components.FolderTile
import com.mateof.tfmtv.ui.components.IconPill
import com.mateof.tfmtv.ui.components.MenuAction
import com.mateof.tfmtv.ui.components.MessageBar
import com.mateof.tfmtv.ui.components.OptionPicker
import com.mateof.tfmtv.ui.components.PickerOption
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.components.SkeletonGrid
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.WideTile
import com.mateof.tfmtv.ui.components.rememberFocusReturn
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.rememberThumbnailsEnabled
import com.mateof.tfmtv.ui.components.returnFocus
import com.mateof.tfmtv.ui.components.subtitle
import com.mateof.tfmtv.ui.components.thumbnailOf
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.palette
import kotlinx.coroutines.delay

@Composable
fun ChannelScreen(
    channelId: Long,
    channelName: String,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit,
    onBack: () -> Unit
) {
    val vm: ChannelViewModel = hiltViewModel()
    val castVm: CastViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var fileMenu by remember { mutableStateOf<ApiFileDto?>(null) }
    var messageMenu by remember { mutableStateOf<ChannelMessageDto?>(null) }
    var sorting by remember { mutableStateOf(false) }
    var focusInside by remember { mutableStateOf(false) }
    val cast: (ApiFileDto) -> Unit = { file -> castVm.open(CastTarget(file, channelId, file.name)) }

    LaunchedEffect(channelId) { vm.start(channelId) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> vm.say(event.message)
                PlayEvent.Handed -> Unit
            }
        }
    }

    BackHandler(enabled = state.tab == ChannelTab.FOLDERS && state.canGoUp) { vm.up() }

    // Opening a folder takes the focused card away: put the focus on the first
    // one of what came, unless there is a card to return to.
    val first = remember { FocusRequester() }
    val tabs = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    var handledMove by remember { mutableStateOf(state.moved) }
    LaunchedEffect(state.loading, state.listing, state.moved) {
        if (state.loading) return@LaunchedEffect
        // After a move between folders the focus is put in the grid even if
        // Compose already gave it to something else when the old card went.
        val moved = state.moved != handledMove
        handledMove = state.moved
        if (!moved && focusInside) return@LaunchedEffect
        delay(140)
        repeat(20) {
            if (gridFocused || (!moved && focusInside)) return@LaunchedEffect
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
        runCatching { tabs.requestFocus() }
    }

    Box(Modifier.fillMaxSize().onFocusChanged { focusInside = it.hasFocus }) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = SIDE, end = SIDE, top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelAvatar(channelId, channelName, 46.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channelName.ifBlank { state.details?.name ?: "Canal" },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val detail = state.rescanMessage ?: listOfNotNull(
                        state.details?.videoCount?.let { if (it == 1L) "1 vídeo" else "$it vídeos" },
                        state.details?.totalSizeText?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.rescanMessage != null) palette.accent else OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                state.details?.let { channel ->
                    IconPill(
                        icon = if (channel.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        label = if (channel.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        selected = channel.isFavorite,
                        onClick = vm::toggleFavorite
                    )
                }
                IconPill(
                    icon = if (state.rescanning) Icons.Outlined.Close else Icons.Outlined.Sync,
                    label = if (state.rescanning) "Cancelar búsqueda" else "Buscar ficheros nuevos",
                    selected = state.rescanning,
                    onClick = vm::rescan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = SIDE, end = SIDE, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelTab.entries.forEach { tab ->
                    TabChip(
                        label = tab.label,
                        selected = state.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        modifier = if (state.tab == tab) Modifier.focusRequester(tabs) else Modifier
                    )
                }
                Spacer(Modifier.weight(1f))
                // Telegram answers the messages newest first, and only like that.
                if (state.tab != ChannelTab.MESSAGES) {
                    TabChip(state.sort.label, selected = false, icon = Icons.Outlined.Sort, opens = true, onClick = { sorting = true })
                }
                SearchField(
                    value = state.search,
                    onValueChange = vm::setSearch,
                    label = when (state.tab) {
                        ChannelTab.FOLDERS -> "Buscar en la carpeta"
                        ChannelTab.VIDEOS -> "Buscar vídeo"
                        ChannelTab.MESSAGES -> "Buscar mensaje"
                    },
                    modifier = Modifier.width(220.dp)
                )
            }

            if (state.tab == ChannelTab.FOLDERS && state.breadcrumbs.size > 1) {
                Text(
                    text = state.breadcrumbs.drop(1).joinToString("  ›  ", prefix = "Inicio  ›  ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = SIDE, end = SIDE, bottom = 6.dp)
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> SkeletonGrid(columns = 4, ratio = 1.3f, modifier = Modifier.padding(start = 20.dp, end = 16.dp))
                    state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
                    state.listing.isEmpty -> EmptyState(
                        when {
                            state.search.isNotBlank() -> "Nada coincide con «${state.search}»."
                            state.tab == ChannelTab.FOLDERS -> "Esta carpeta no contiene vídeos."
                            state.tab == ChannelTab.VIDEOS -> "No hay vídeos indexados en este canal. Prueba con «Buscar ficheros nuevos»."
                            else -> "Este canal no tiene mensajes con vídeo."
                        }
                    )
                    else -> Grid(
                        modifier = Modifier.onFocusChanged { gridFocused = it.hasFocus },
                        state = state,
                        vm = vm,
                        channelId = channelId,
                        first = first,
                        onFile = {
                            vm.lastFocus = "f-${it.id}"
                            vm.play(it)
                        },
                        onFileMenu = {
                            vm.lastFocus = "f-${it.id}"
                            fileMenu = it
                        },
                        onMessage = {
                            vm.lastFocus = "m-${it.id}"
                            vm.play(it)
                        },
                        onMessageMenu = {
                            vm.lastFocus = "m-${it.id}"
                            messageMenu = it
                        }
                    )
                }
            }
        }
        MessageBar(state.message, vm::messageShown)
    }

    fileMenu?.let { file ->
        ActionMenu(
            title = file.name,
            subtitle = file.subtitle(),
            onDismiss = { fileMenu = null },
            actions = listOf(
                MenuAction("Reproducir", Icons.Outlined.PlayArrow) { vm.play(file) },
                MenuAction("Ver desde el principio", Icons.Outlined.Replay) { vm.play(file, startMs = 0L) },
                MenuAction("Enviar a la tele", Icons.Outlined.Cast) { cast(file) }
            )
        )
    }
    messageMenu?.let { message ->
        val file = vm.fileOf(message)
        ActionMenu(
            title = message.displayName(),
            subtitle = message.summary(),
            onDismiss = { messageMenu = null },
            actions = buildList {
                add(MenuAction("Reproducir", Icons.Outlined.PlayArrow) { vm.play(message) })
                if (file != null) add(MenuAction("Ver desde el principio", Icons.Outlined.Replay) { vm.play(file, startMs = 0L) })
                add(MenuAction("Enviar a la tele", Icons.Outlined.Cast) {
                    vm.castable(message)?.let(cast) ?: vm.say("El vídeo del mensaje no está indexado; ábrelo antes para localizarlo.")
                })
            }
        )
    }
    if (sorting) OptionPicker(
        title = "Ordenar por",
        options = SortOption.entries.map { PickerOption(it, it.label) },
        selected = state.sort,
        onSelect = vm::setSort,
        onDismiss = { sorting = false }
    )
    CastSheet(castVm, onOpenPanel = onOpenCastPanel)
}

@Composable
private fun Grid(
    modifier: Modifier,
    state: ChannelState,
    vm: ChannelViewModel,
    channelId: Long,
    first: FocusRequester,
    onFile: (ApiFileDto) -> Unit,
    onFileMenu: (ApiFileDto) -> Unit,
    onMessage: (ChannelMessageDto) -> Unit,
    onMessageMenu: (ChannelMessageDto) -> Unit
) {
    val listing = state.listing
    val urls = rememberMediaUrls()
    val showThumbnails = rememberThumbnailsEnabled()
    // Each folder and each tab keeps its own place.
    val where = "${state.tab}-${if (state.tab == ChannelTab.FOLDERS) state.folderId ?: state.path else ""}-${state.sort}-${state.search}"
    val gridState = rememberSaveable(where, saver = LazyGridState.Saver) { LazyGridState() }
    val back: FocusReturn = rememberFocusReturn(vm.lastFocus, where)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - PREFETCH
        }.collect { near -> if (near) vm.loadMore() }
    }
    // A page that fills nothing leaves the end in sight: the flow above does not change.
    LaunchedEffect(listing) {
        val info = gridState.layoutInfo
        if (info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - PREFETCH) vm.loadMore()
    }

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(180.dp),
        state = gridState,
        contentPadding = PaddingValues(start = SIDE, end = SIDE, top = 8.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(listing.folders, key = { _, f -> "d-${f.id}" }) { index, folder ->
            FolderTile(
                name = folder.name,
                subtitle = "Carpeta",
                onClick = { vm.openFolder(folder) },
                modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).returnFocus(back, "d-${folder.id}")
            )
        }
        itemsIndexed(listing.videos, key = { _, f -> "f-${f.id}" }) { index, file ->
            WideTile(
                title = file.name,
                subtitle = file.subtitle(),
                imageUrl = thumbnailOf(file),
                onClick = { onFile(file) },
                onLongClick = { onFileMenu(file) },
                modifier = (if (index == 0 && listing.folders.isEmpty()) Modifier.focusRequester(first) else Modifier)
                    .returnFocus(back, "f-${file.id}")
            )
        }
        itemsIndexed(listing.messages, key = { _, m -> "m-${m.id}" }) { index, message ->
            WideTile(
                title = message.displayName(),
                subtitle = message.summary(),
                badge = message.duration?.takeIf { it > 0 }?.let { Format.duration(it * 1000L) },
                imageUrl = if (showThumbnails && state.messageThumbnails) urls.messageThumbnail(channelId, message.id) else null,
                onClick = { onMessage(message) },
                onLongClick = { onMessageMenu(message) },
                modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).returnFocus(back, "m-${message.id}")
            )
        }
        if (listing.next != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(26.dp), color = palette.accent, strokeWidth = 3.dp)
            }
        }
    }
}

private fun ChannelMessageDto.summary(): String = listOfNotNull(
    Format.day(date).takeIf { it.isNotBlank() },
    Format.bytes(fileSize).takeIf { it.isNotBlank() }
).joinToString(" · ")

private val SIDE = 48.dp

/** Cards left below the last visible one when the next page is asked for. */
private const val PREFETCH = 9
