package com.mateof.tfmtv.ui.screens.channel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.FolderCard
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.VideoCard
import com.mateof.tfmtv.ui.components.subtitle
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.media.PlayEvent

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
    var message by remember { mutableStateOf<String?>(null) }
    val cast: (ApiFileDto) -> Unit = { file -> castVm.open(CastTarget(file, channelId, file.name)) }

    LaunchedEffect(channelId) { vm.start(channelId) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> message = event.message
                PlayEvent.Handed -> Unit
            }
        }
    }

    BackHandler(enabled = state.tab == ChannelTab.FOLDERS && state.canGoUp) { vm.up() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channelName.ifBlank { "Canal" },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TabChip(
                label = if (state.rescanning) "Escaneando…" else "Buscar ficheros nuevos",
                selected = state.rescanning,
                onClick = vm::rescan
            )
        }
        Text(
            text = "Mantén pulsado un vídeo para enviarlo a la tele (DLNA).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp, top = 4.dp)
        )
        state.rescanMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, top = 6.dp)
            )
        }

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

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 40.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Orden",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
            )
            SortOption.entries.forEach { option ->
                TabChip(
                    label = option.label,
                    selected = state.sort == option,
                    onClick = { vm.setSort(option) }
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
                    ChannelTab.FOLDERS -> FolderTab(state, vm, cast)
                    ChannelTab.VIDEOS -> VideoGrid(
                        videos = state.allVideos,
                        empty = "No hay vídeos indexados en este canal.",
                        vm = vm,
                        onCast = cast
                    )
                    ChannelTab.MESSAGES -> MessagesTab(state, vm) { msg ->
                        state.filesByMessage[msg.id]?.let(cast)
                            ?: run { message = "El vídeo del mensaje no está indexado; ábrelo antes para localizarlo." }
                    }
                }
            }
        }
    }

    CastSheet(castVm, onOpenPanel = onOpenCastPanel)
}

@Composable
private fun FolderTab(state: ChannelState, vm: ChannelViewModel, onCast: (ApiFileDto) -> Unit) {
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
                onClick = { vm.openFolder(folderId = folder.id) }
            )
        }
        items(state.videos, key = { "f-${it.id}" }) { file ->
            VideoCard(
                title = file.name,
                subtitle = file.subtitle(),
                onClick = { vm.play(file) },
                onLongClick = { onCast(file) }
            )
        }
    }
}

@Composable
private fun VideoGrid(videos: List<ApiFileDto>, empty: String, vm: ChannelViewModel, onCast: (ApiFileDto) -> Unit) {
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
                onClick = { vm.play(file) },
                onLongClick = { onCast(file) }
            )
        }
    }
}

@Composable
private fun MessagesTab(state: ChannelState, vm: ChannelViewModel, onCast: (ChannelMessageDto) -> Unit) {
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
        items(state.sortedMessages, key = { it.id }) { msg ->
            VideoCard(
                title = msg.fileName?.takeIf { it.isNotBlank() }
                    ?: msg.text?.takeIf { it.isNotBlank() }
                    ?: "Mensaje ${msg.id}",
                subtitle = listOfNotNull(
                    Format.date(msg.date).takeIf { it.isNotBlank() },
                    Format.bytes(msg.fileSize).takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                onClick = { vm.play(msg) },
                onLongClick = { onCast(msg) }
            )
        }
    }
}
