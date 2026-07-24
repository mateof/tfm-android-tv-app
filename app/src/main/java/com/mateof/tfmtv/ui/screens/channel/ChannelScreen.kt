package com.mateof.tfmtv.ui.screens.channel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.FolderCard
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.VideoCard
import com.mateof.tfmtv.ui.components.subtitle
import com.mateof.tfmtv.ui.components.tapClick

@Composable
fun ChannelScreen(
    channelId: Long,
    channelName: String,
    onPlayInternal: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val vm: ChannelViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channelId) { vm.start(channelId) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event.url, event.title)
                is PlayEvent.Failed -> message = event.message
                PlayEvent.Handed -> Unit
            }
        }
    }

    BackHandler(enabled = state.tab == ChannelTab.FOLDERS && state.path != "/") { vm.up() }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = channelName.ifBlank { "Canal" },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 40.dp, top = 28.dp)
        )

        Row(
            modifier = Modifier.padding(start = 40.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChannelTab.entries.forEach { tab ->
                TabChip(
                    label = tab.label,
                    selected = state.tab == tab,
                    onClick = { vm.selectTab(tab) }
                )
            }
        }

        if (state.tab == ChannelTab.FOLDERS && state.breadcrumbs.size > 1) {
            Text(
                text = state.breadcrumbs.joinToString(" / ") { it.name.ifBlank { "raíz" } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)
            )
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
            when {
                state.loading -> Loading()
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.selectTab(state.tab) })
                else -> when (state.tab) {
                    ChannelTab.FOLDERS -> FolderTab(state, vm)
                    ChannelTab.VIDEOS -> VideoGrid(
                        videos = state.allVideos,
                        empty = "No hay vídeos indexados en este canal.",
                        vm = vm
                    )
                    ChannelTab.MESSAGES -> MessagesTab(state, vm)
                }
            }
        }
    }
}

@Composable
private fun FolderTab(state: ChannelState, vm: ChannelViewModel) {
    if (state.folders.isEmpty() && state.videos.isEmpty()) {
        EmptyState("Esta carpeta no contiene vídeos.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(state.folders, key = { "d-${it.id}" }) { folder ->
            FolderCard(
                name = folder.name,
                subtitle = "Carpeta",
                onClick = { vm.openFolder(folder.path ?: "/") }
            )
        }
        items(state.videos, key = { "f-${it.id}" }) { file ->
            VideoCard(
                title = file.name,
                subtitle = file.subtitle(),
                onClick = { vm.play(file) }
            )
        }
    }
}

@Composable
private fun VideoGrid(videos: List<ApiFileDto>, empty: String, vm: ChannelViewModel) {
    if (videos.isEmpty()) {
        EmptyState(empty)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(videos, key = { it.id }) { file ->
            VideoCard(
                title = file.name,
                subtitle = file.subtitle(),
                onClick = { vm.play(file) }
            )
        }
    }
}

@Composable
private fun MessagesTab(state: ChannelState, vm: ChannelViewModel) {
    if (state.messages.isEmpty()) {
        EmptyState("Este canal no tiene mensajes con vídeo.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(state.messages, key = { it.id }) { msg ->
            VideoCard(
                title = msg.fileName?.takeIf { it.isNotBlank() }
                    ?: msg.text?.takeIf { it.isNotBlank() }
                    ?: "Mensaje ${msg.id}",
                subtitle = listOfNotNull(
                    Format.date(msg.date).takeIf { it.isNotBlank() },
                    Format.bytes(msg.fileSize).takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                onClick = { vm.play(msg) }
            )
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}
