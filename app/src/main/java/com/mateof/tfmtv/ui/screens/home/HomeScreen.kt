package com.mateof.tfmtv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Tv
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.ui.components.ChannelCard
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.FolderCard
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.NavRail
import com.mateof.tfmtv.ui.components.RailItem
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.screens.settings.SettingsContent

@Composable
fun HomeScreen(
    onChannel: (Long, String) -> Unit,
    onReconfigure: () -> Unit
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var railExpanded by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        NavRail(expanded = railExpanded, onExpandedChange = { railExpanded = it }) {
            HomeSection.entries.forEach { section ->
                RailItem(
                    icon = section.icon(),
                    label = section.label,
                    selected = state.section == section,
                    expanded = railExpanded,
                    onClick = { vm.select(section) }
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.section == HomeSection.SETTINGS ->
                    SettingsContent(onReconfigure = onReconfigure)

                state.loading -> Loading()

                state.error != null -> ErrorState(state.error!!, onRetry = vm::load)

                state.section == HomeSection.FOLDERS && state.openFolderId == null ->
                    FolderGrid(state, onOpen = vm::openFolder)

                else -> ChannelGrid(
                    title = sectionTitle(state),
                    channels = state.visibleChannels,
                    search = state.search,
                    onSearch = vm::setSearch,
                    onChannel = onChannel
                )
            }
        }
    }
}

private fun sectionTitle(state: HomeState): String =
    if (state.section == HomeSection.FOLDERS) {
        state.folders.firstOrNull { it.id == state.openFolderId }?.title.orEmpty()
    } else {
        state.section.label
    }

@Composable
private fun ChannelGrid(
    title: String,
    channels: List<com.mateof.tfmtv.data.model.ChannelDto>,
    search: String,
    onSearch: (String) -> Unit,
    onChannel: (Long, String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            SearchField(
                value = search,
                onValueChange = onSearch,
                label = "Buscar canal",
                modifier = Modifier.width(360.dp)
            )
        }
        if (channels.isEmpty()) {
            EmptyState(
                if (search.isBlank()) "No hay canales en esta sección."
                else "Ningún canal coincide con «$search»."
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { onChannel(channel.id, channel.name.orEmpty()) }
                )
            }
        }
    }
}

@Composable
private fun FolderGrid(state: HomeState, onOpen: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Carpetas",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 40.dp, top = 32.dp, bottom = 16.dp)
        )
        if (state.folders.isEmpty()) {
            EmptyState("El servidor no ha devuelto carpetas de Telegram.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(state.folders, key = { it.id }) { folder ->
                FolderCard(
                    name = listOfNotNull(folder.iconEmoji, folder.title).joinToString(" "),
                    subtitle = "${state.channelCount(folder)} canales",
                    onClick = { onOpen(folder.id) }
                )
            }
        }
    }
}

private fun HomeSection.icon() = when (this) {
    HomeSection.MINE -> Icons.Outlined.Subscriptions
    HomeSection.SHARED -> Icons.Outlined.Tv
    HomeSection.FAVORITES -> Icons.Outlined.Star
    HomeSection.FOLDERS -> Icons.Outlined.Folder
    HomeSection.ALL -> Icons.Outlined.Apps
    HomeSection.SETTINGS -> Icons.Outlined.Settings
}
