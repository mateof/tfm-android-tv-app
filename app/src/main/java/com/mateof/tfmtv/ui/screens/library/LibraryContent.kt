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
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.setValue
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
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.Loading
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
    onPlayInternal: (PlayEvent.Internal) -> Unit
) {
    val vm: LibraryViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    // Coming back from the player or the identify screen: progress may have
    // changed, and the focus must land on the tabs, not on the search field
    // (a focused text field pops the TV keyboard over everything).
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        vm.refresh()
        delay(120)
        runCatching { tabFocus.requestFocus() }
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
                modifier = Modifier.weight(1f)
            )
            SearchField(
                value = state.search,
                onValueChange = vm::setSearch,
                label = "Buscar título",
                modifier = Modifier.width(360.dp)
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
                state.unavailable != null -> EmptyState(state.unavailable!!)
                state.error != null -> ErrorState(state.error!!, onRetry = vm::refresh)
                else -> when (state.tab) {
                    LibraryTab.CONTINUE -> ContinueGrid(state.visibleContinue, state.search, onPlay = vm::play)
                    LibraryTab.MOVIES, LibraryTab.SERIES -> ItemGrid(state.visibleItems, state.tab, state.search, onOpenItem)
                    LibraryTab.REVIEW -> ReviewList(state.visibleReview, state.search, onIdentify)
                }
            }
        }
    }
}

@Composable
private fun ContinueGrid(files: List<LibraryFileDto>, search: String, onPlay: (LibraryFileDto) -> Unit) {
    if (files.isEmpty()) {
        EmptyState(
            if (search.isBlank()) "Nada a medias. Lo que dejes empezado aparecerá aquí."
            else "Nada coincide con «$search»."
        )
        return
    }
    val urls = rememberMediaUrls()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(files, key = { "${it.channelId}-${it.fileId}" }) { file ->
            val item = file.item
            val subtitle = when {
                item != null && item.kind == LibraryKind.SERIES && file.episode != null ->
                    "T${file.season ?: 1} · E${file.episode}"
                item != null -> item.year?.toString()
                else -> file.sizeText
            }
            PosterCard(
                title = item?.title ?: file.fileName,
                subtitle = subtitle,
                imageUrl = urls.absolute(item?.posterUrl),
                progress = file.watch?.progress?.toFloat(),
                completed = file.watch?.completed == true,
                placeholder = if (item?.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onPlay(file) }
            )
        }
    }
}

@Composable
private fun ItemGrid(items: List<LibraryItemDto>, tab: LibraryTab, search: String, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        EmptyState(
            when {
                search.isNotBlank() -> "Nada coincide con «$search»."
                tab == LibraryTab.MOVIES -> "Todavía no hay películas identificadas. Lanza un escaneo desde la web del servidor."
                else -> "Todavía no hay series identificadas. Lanza un escaneo desde la web del servidor."
            }
        )
        return
    }
    val urls = rememberMediaUrls()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
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
            PosterCard(
                title = item.title,
                subtitle = subtitle,
                imageUrl = urls.absolute(item.posterUrl),
                progress = if (item.watch.inProgress) item.watch.progress.toFloat() else null,
                completed = item.watch.completed,
                placeholder = if (series) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onOpen(item.id) }
            )
        }
    }
}

@Composable
private fun ReviewList(files: List<LibraryFileDto>, search: String, onIdentify: (IdentifyTarget) -> Unit) {
    if (files.isEmpty()) {
        EmptyState(
            if (search.isBlank()) "No hay ficheros pendientes de revisar."
            else "Nada coincide con «$search»."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { "${it.channelId}-${it.fileId}" }) { file ->
            ReviewRow(file) {
                onIdentify(
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
        }
    }
}

@Composable
private fun ReviewRow(file: LibraryFileDto, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
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
