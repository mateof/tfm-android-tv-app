package com.mateof.tfmtv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.LibraryEpisodeDto
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDetailDto
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.PosterImage
import com.mateof.tfmtv.ui.components.ProgressBar
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.tapClick
import com.mateof.tfmtv.ui.theme.Background
import kotlinx.coroutines.delay

@Composable
fun LibraryItemScreen(
    itemId: String,
    onIdentify: (IdentifyTarget) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onBack: () -> Unit
) {
    val vm: LibraryItemViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    // Reloads on every entry so progress recorded by the player shows up
    LaunchedEffect(itemId) { vm.load(itemId) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> message = event.message
                PlayEvent.Handed -> Unit
            }
        }
    }

    val item = state.item
    when {
        state.loading && item == null -> { Loading(); return }
        state.error != null && item == null -> { ErrorState(state.error!!, onRetry = { vm.load(itemId) }); return }
        item == null -> { onBack(); return }
    }

    val urls = rememberMediaUrls()
    val playFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(item.id) {
        runCatching { playFocus.requestFocus() }
        // Focusing scrolls the button into view; put the header back on screen
        delay(60)
        listState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        AsyncImage(
            model = urls.absolute(item.backdropUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.35f)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.55f to Background.copy(alpha = 0.85f), 1f to Background)
            )
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header and buttons share one item so focusing a button keeps the title in view
            item {
                Header(item, urls.absolute(item.posterUrl))
                ActionRow(
                    item = item,
                    vm = vm,
                    playFocus = playFocus,
                    onIdentify = {
                        onIdentify(IdentifyTarget(itemId = item.id, kind = item.kind, query = item.title))
                    }
                )
            }
            (state.message ?: message)?.let { text ->
                item {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            if (item.isSeries) {
                if (item.seasons.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item.seasons.forEach { season ->
                                TabChip(
                                    label = season.name ?: "Temporada ${season.number}",
                                    selected = state.season == season.number,
                                    onClick = { vm.selectSeason(season.number) }
                                )
                            }
                        }
                    }
                }
                val episodes = state.episodes
                items(episodes, key = { "${it.season}-${it.number}" }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        stillUrl = urls.absolute(episode.stillUrl),
                        isNext = item.nextUp?.let { it.season == episode.season && it.number == episode.number } == true,
                        onClick = { vm.episodeFile(episode)?.let { (file, start) -> vm.play(file, start) } }
                    )
                }
            } else if (item.files.size > 1) {
                item {
                    Text(
                        "Versiones",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(item.files, key = { "${it.channelId}-${it.fileId}" }) { file ->
                    VersionRow(file, onClick = { vm.play(file, file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L) })
                }
            }
        }
    }
}

@Composable
private fun Header(item: LibraryItemDetailDto, posterUrl: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(
            Modifier
                .width(200.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PosterImage(posterUrl, if (item.isSeries) Icons.Outlined.Tv else Icons.Outlined.Movie)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!item.originalTitle.isNullOrBlank() && !item.originalTitle.equals(item.title, ignoreCase = true)) {
                Text(item.originalTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val meta = listOfNotNull(
                item.year?.toString(),
                item.runtime?.let { runtimeText(it) },
                item.genres.take(3).joinToString(", ").takeIf { it.isNotBlank() },
                item.rating?.let { "★ %.1f".format(it) },
                if (item.isSeries && item.watch.episodesTotal > 0)
                    "${item.watch.episodesWatched}/${item.watch.episodesTotal} episodios vistos" else null
            ).joinToString("  ·  ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.tagline?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            }
            item.overview?.takeIf { it.isNotBlank() }?.let { Overview(it) }
        }
    }
}

/** The synopsis is focusable so a long one can be expanded with the D-pad. */
@Composable
private fun Overview(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.tapClick { expanded = !expanded },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun ActionRow(
    item: LibraryItemDetailDto,
    vm: LibraryItemViewModel,
    playFocus: FocusRequester,
    onIdentify: () -> Unit
) {
    val primary = vm.primaryFile()
    val resume = item.resume
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val playLabel = when {
            resume != null && (resume.watch?.positionMs ?: 0L) > 0 ->
                "Continuar (${Format.duration(resume.watch!!.positionMs)})"
            item.isSeries && item.nextUp != null -> "Reproducir T${item.nextUp.season} E${item.nextUp.number}"
            else -> "Reproducir"
        }
        val play = { primary?.let { (file, start) -> vm.play(file, start) } ?: Unit }
        ActionButton(playLabel, Icons.Outlined.PlayArrow, enabled = primary != null, onClick = play,
            modifier = Modifier.focusRequester(playFocus))
        ActionButton("Ver desde el principio", Icons.Outlined.Replay, enabled = primary != null) {
            primary?.let { (file, _) -> vm.play(file, 0L) }
        }
        val watched = item.watch.completed
        ActionButton(
            label = if (watched) "Marcar como no vista" else "Marcar como vista",
            icon = if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            enabled = true,
            onClick = vm::toggleWatched
        )
        ActionButton("Corregir identificación", Icons.Outlined.Edit, enabled = true, onClick = onIdentify)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.tapClick(onClick)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun EpisodeRow(episode: LibraryEpisodeDto, stillUrl: String?, isNext: Boolean, onClick: () -> Unit) {
    val progress = episode.watch?.takeIf { it.inProgress }?.progress?.toFloat()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PosterImage(stillUrl, Icons.Outlined.Tv)
                if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "T${episode.season} · E${episode.number}" + (episode.title?.let { "  $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    episode.runtime?.let { runtimeText(it) },
                    episode.airDate?.let { Format.date(it).substringBefore(' ') }?.takeIf { it.isNotBlank() },
                    if (isNext) "Siguiente" else null,
                    if (episode.files.isEmpty()) "Sin fichero" else null
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                episode.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (episode.completed) {
                Icon(Icons.Outlined.Check, contentDescription = "Vista", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun VersionRow(file: LibraryFileDto, onClick: () -> Unit) {
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(file.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(file.sizeText ?: Format.bytes(file.size), style = MaterialTheme.typography.bodySmall)
                if (file.watch?.completed == true) Icon(Icons.Outlined.Check, contentDescription = "Vista", modifier = Modifier.size(20.dp))
            }
            file.watch?.takeIf { it.inProgress }?.let {
                Spacer(Modifier.height(6.dp))
                ProgressBar(it.progress.toFloat())
            }
        }
    }
}

private fun runtimeText(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
