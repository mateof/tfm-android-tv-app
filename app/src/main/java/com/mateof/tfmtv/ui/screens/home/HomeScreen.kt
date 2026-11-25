package com.mateof.tfmtv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.cast.CastPanelViewModel
import com.mateof.tfmtv.ui.components.ActionMenu
import com.mateof.tfmtv.ui.components.ChannelTile
import com.mateof.tfmtv.ui.components.ContentEnd
import com.mateof.tfmtv.ui.components.ContentStart
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.FolderTile
import com.mateof.tfmtv.ui.components.LocalEntryFocus
import com.mateof.tfmtv.ui.components.MenuAction
import com.mateof.tfmtv.ui.components.MessageBar
import com.mateof.tfmtv.ui.components.NavRail
import com.mateof.tfmtv.ui.components.RailDivider
import com.mateof.tfmtv.ui.components.RailItem
import com.mateof.tfmtv.ui.components.RailWidth
import com.mateof.tfmtv.ui.components.ScreenHeader
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.components.SkeletonGrid
import com.mateof.tfmtv.ui.components.entryFocus
import com.mateof.tfmtv.ui.components.rememberFocusReturn
import com.mateof.tfmtv.ui.components.returnFocus
import com.mateof.tfmtv.ui.screens.library.IdentifyTarget
import com.mateof.tfmtv.ui.screens.library.LibraryContent
import com.mateof.tfmtv.ui.screens.search.SearchContent
import com.mateof.tfmtv.ui.screens.settings.SettingsContent
import com.mateof.tfmtv.ui.theme.palette
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onChannel: (Long, String) -> Unit,
    onReconfigure: () -> Unit,
    onLibraryItem: (String) -> Unit,
    onIdentify: (IdentifyTarget) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val castVm: CastPanelViewModel = hiltViewModel()
    val casting by castVm.session.collectAsStateWithLifecycle()

    var railExpanded by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    var channelMenu by remember { mutableStateOf<ChannelDto?>(null) }
    val railFocus = remember { FocusRequester() }
    val entry = remember { FocusRequester() }
    val content = remember { FocusRequester() }

    // Counts the times the content has been asked to take the focus: on opening
    // and whenever a section is picked. Any key in the rail gives up the wait.
    var enter by remember { mutableIntStateOf(1) }
    LaunchedEffect(enter) {
        if (enter == 0) return@LaunchedEffect
        contentFocused = false
        repeat(60) { attempt ->
            delay(if (attempt == 0) 180 else 120)
            if (contentFocused) return@LaunchedEffect
            // The card of the section, or whatever there is if it takes too long.
            val asked = runCatching { entry.requestFocus() }.isSuccess
            if (!asked && attempt > 25) runCatching { content.requestFocus() }
        }
    }

    // BACK: out of the folder, then to the rail, then to the start, then out of the app.
    val inFolder = state.section == HomeSection.FOLDERS && state.openFolderId != null
    BackHandler(enabled = inFolder || contentFocused || state.section != HomeSection.START) {
        when {
            inFolder && contentFocused -> {
                vm.openFolder(null)
                enter = (enter + 1).coerceAtLeast(1)
            }
            contentFocused -> runCatching { railFocus.requestFocus() }
            else -> vm.select(HomeSection.START)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = RailWidth)
                .focusRequester(content)
                .onFocusChanged { contentFocused = it.hasFocus }
                .focusGroup()
        ) {
            val effects = palette.effects
            AnimatedContent(
                targetState = state.section,
                transitionSpec = {
                    if (effects) fadeIn(tween(260, 60)) togetherWith fadeOut(tween(90))
                    else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                },
                label = "section"
            ) { section ->
                // The section on its way out must not answer to the request.
                val own = remember { FocusRequester() }
                CompositionLocalProvider(LocalEntryFocus provides if (section == state.section) entry else own) {
                    Section(
                        section = section,
                        state = state,
                        vm = vm,
                        onChannel = onChannel,
                        onChannelMenu = { channelMenu = it },
                        onReconfigure = onReconfigure,
                        onLibraryItem = onLibraryItem,
                        onIdentify = onIdentify,
                        onPlayInternal = onPlayInternal,
                        onOpenCastPanel = onOpenCastPanel,
                        onEnter = { enter = (enter + 1).coerceAtLeast(1) }
                    )
                }
            }
        }

        NavRail(
            expanded = railExpanded,
            onExpandedChange = { railExpanded = it },
            selected = railFocus,
            modifier = Modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) enter = 0
                false
            }
        ) {
            HomeSection.entries.forEach { section ->
                if (section == HomeSection.FAVORITES || section == HomeSection.SETTINGS) RailDivider()
                RailItem(
                    icon = section.icon(),
                    label = section.label,
                    selected = state.section == section,
                    expanded = railExpanded,
                    onClick = {
                        vm.select(section)
                        enter = (enter + 1).coerceAtLeast(1)
                    },
                    modifier = if (state.section == section) Modifier.focusRequester(railFocus) else Modifier
                )
            }
            // Only while something is on a television: the way back to its remote.
            casting?.let { session ->
                RailItem(
                    icon = Icons.Outlined.Cast,
                    label = "En ${session.device.name}",
                    selected = false,
                    expanded = railExpanded,
                    onClick = onOpenCastPanel
                )
            }
        }

        MessageBar(state.message, vm::messageShown)
    }

    channelMenu?.let { channel ->
        ActionMenu(
            title = channel.name.orEmpty(),
            onDismiss = { channelMenu = null },
            actions = listOf(
                MenuAction("Abrir", Icons.AutoMirrored.Outlined.OpenInNew) { onChannel(channel.id, channel.name.orEmpty()) },
                MenuAction(
                    if (channel.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    if (channel.isFavorite) Icons.Outlined.StarBorder else Icons.Outlined.Star
                ) {
                    vm.toggleFavorite(channel)
                    // In Favourites the card goes away, and the focus with it.
                    enter = (enter + 1).coerceAtLeast(1)
                },
                MenuAction(
                    if (channel.isHidden) "Dejar de ocultar" else "Ocultar canal",
                    if (channel.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                ) {
                    vm.toggleHidden(channel)
                    enter = (enter + 1).coerceAtLeast(1)
                }
            )
        )
    }
}

@Composable
private fun Section(
    section: HomeSection,
    state: HomeState,
    vm: HomeViewModel,
    onChannel: (Long, String) -> Unit,
    onChannelMenu: (ChannelDto) -> Unit,
    onReconfigure: () -> Unit,
    onLibraryItem: (String) -> Unit,
    onIdentify: (IdentifyTarget) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit,
    onEnter: () -> Unit
) {
    when (section) {
        HomeSection.START -> StartContent(
            favorites = state.favorites,
            ownChannels = state.mine,
            onChannel = onChannel,
            onChannelMenu = onChannelMenu,
            onOpenItem = onLibraryItem,
            onIdentify = onIdentify,
            onPlayInternal = onPlayInternal,
            onOpenCastPanel = onOpenCastPanel
        )

        HomeSection.SEARCH -> SearchContent(
            channels = state.listed,
            onChannel = onChannel,
            onChannelMenu = onChannelMenu,
            onOpenItem = onLibraryItem,
            onPlayInternal = onPlayInternal,
            onOpenCastPanel = onOpenCastPanel
        )

        HomeSection.LIBRARY -> LibraryContent(
            onOpenItem = onLibraryItem,
            onIdentify = onIdentify,
            onPlayInternal = onPlayInternal,
            onOpenCastPanel = onOpenCastPanel
        )

        HomeSection.SETTINGS -> SettingsContent(onReconfigure = onReconfigure)

        else -> when {
            state.loading -> SkeletonGrid(columns = 3, ratio = 3.1f, modifier = Modifier.padding(top = 70.dp), rows = 4)
            state.error != null -> ErrorState(state.error, onRetry = vm::load)
            section == HomeSection.FOLDERS && state.openFolderId == null -> FolderGrid(state, vm.focusOf(section)) {
                vm.openFolder(it)
                onEnter()
            }
            else -> ChannelGrid(
                section = section,
                title = if (section == HomeSection.FOLDERS) state.folders.firstOrNull { it.id == state.openFolderId }?.title.orEmpty() else section.label,
                state = state,
                focusKey = vm.focusOf(section),
                onSearch = vm::setSearch,
                onChannel = { channel ->
                    vm.saveFocus("channel-${channel.id}")
                    onChannel(channel.id, channel.name.orEmpty())
                },
                onMenu = onChannelMenu
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    section: HomeSection,
    title: String,
    state: HomeState,
    focusKey: String?,
    onSearch: (String) -> Unit,
    onChannel: (ChannelDto) -> Unit,
    onMenu: (ChannelDto) -> Unit
) {
    val channels = state.visibleChannels(section)
    val back = rememberFocusReturn(focusKey)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, detail = if (channels.size == 1) "1 canal" else "${channels.size} canales") {
            SearchField(
                value = state.search,
                onValueChange = onSearch,
                label = "Buscar canal",
                modifier = Modifier.width(260.dp)
            )
        }
        if (channels.isEmpty()) {
            EmptyState(
                when {
                    state.search.isNotBlank() -> "Ningún canal coincide con «${state.search}»."
                    section == HomeSection.FAVORITES ->
                        "Todavía no hay canales favoritos. Mantén pulsado un canal para añadirlo."
                    else -> "No hay canales en esta sección."
                }
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            state = rememberLazyGridState(),
            contentPadding = PaddingValues(start = ContentStart, end = ContentEnd, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                ChannelTile(
                    channel = channel,
                    onClick = { onChannel(channel) },
                    onLongClick = { onMenu(channel) },
                    modifier = Modifier.entryFocus(index == 0).returnFocus(back, "channel-${channel.id}")
                )
            }
        }
    }
}

@Composable
private fun FolderGrid(state: HomeState, focusKey: String?, onOpen: (Long) -> Unit) {
    val back = rememberFocusReturn(focusKey)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Carpetas")
        if (state.folders.isEmpty()) {
            EmptyState("El servidor no ha devuelto carpetas de Telegram.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(start = ContentStart, end = ContentEnd, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(state.folders, key = { _, f -> f.id }) { index, folder ->
                val count = state.channelCount(folder)
                FolderTile(
                    name = folder.title.orEmpty(),
                    emoji = folder.iconEmoji?.takeIf { it.isNotBlank() },
                    subtitle = if (count == 1) "1 canal" else "$count canales",
                    onClick = { onOpen(folder.id) },
                    modifier = Modifier.entryFocus(index == 0).returnFocus(back, "folder-${folder.id}")
                )
            }
        }
    }
}

private fun HomeSection.icon() = when (this) {
    HomeSection.START -> Icons.Outlined.Home
    HomeSection.SEARCH -> Icons.Outlined.Search
    HomeSection.LIBRARY -> Icons.Outlined.VideoLibrary
    HomeSection.FAVORITES -> Icons.Outlined.Star
    HomeSection.MINE -> Icons.Outlined.Subscriptions
    HomeSection.SHARED -> Icons.Outlined.People
    HomeSection.FOLDERS -> Icons.Outlined.Folder
    HomeSection.ALL -> Icons.Outlined.Apps
    HomeSection.SETTINGS -> Icons.Outlined.Settings
}
