package com.mateof.tfmtv.ui.screens.library

import com.mateof.tfmtv.ui.components.listRowBorder
import com.mateof.tfmtv.ui.components.listRowColors
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.LibraryEpisodeDto
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDetailDto
import com.mateof.tfmtv.data.model.LibrarySeasonDto
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.ActionMenu
import com.mateof.tfmtv.ui.components.Art
import com.mateof.tfmtv.ui.components.Badge
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.HeroBackdrop
import com.mateof.tfmtv.ui.components.IconPill
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.MenuAction
import com.mateof.tfmtv.ui.components.MessageBar
import com.mateof.tfmtv.ui.components.PillButton
import com.mateof.tfmtv.ui.components.ProgressBar
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.rememberFocusReturn
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.requestSoon
import com.mateof.tfmtv.ui.components.returnFocus
import com.mateof.tfmtv.ui.components.tapClick
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.Star
import com.mateof.tfmtv.ui.theme.palette
import kotlinx.coroutines.delay

@Composable
fun LibraryItemScreen(
    itemId: String,
    onIdentify: (IdentifyTarget) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit,
    onBack: () -> Unit
) {
    val vm: LibraryItemViewModel = hiltViewModel()
    val castVm: CastViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var episodeMenu by remember { mutableStateOf<LibraryEpisodeDto?>(null) }
    var fileMenu by remember { mutableStateOf<LibraryFileDto?>(null) }
    var seasonMenu by remember { mutableStateOf<LibrarySeasonDto?>(null) }

    // Reloads on every entry so progress recorded by the player shows up
    LaunchedEffect(itemId) { vm.load(itemId) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> vm.say(event.message)
                PlayEvent.Handed -> Unit
            }
        }
    }

    val item = state.item
    when {
        state.loading && item == null -> { Loading(); return }
        state.error != null && item == null -> { ErrorState(state.error!!, onRetry = { vm.load(itemId) }); return }
        item == null -> { LaunchedEffect(Unit) { onBack() }; return }
    }

    val urls = rememberMediaUrls()
    val playFocus = remember { FocusRequester() }
    val back = rememberFocusReturn(vm.lastFocus)
    val listState = rememberLazyListState()
    LaunchedEffect(item.id) {
        if (vm.lastFocus != null) return@LaunchedEffect
        playFocus.requestSoon()
        // Focusing scrolls the button into view; put the header back on screen
        delay(60)
        listState.scrollToItem(0)
    }
    val cast: (LibraryFileDto, Long) -> Unit = { file, start ->
        castVm.open(CastTarget(file.file, file.channelId, vm.titleFor(file), start))
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(urls.libraryImage(item.backdropUrl, "w1280"), height = 0.86f, settleMs = 0)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = SIDE, end = SIDE, top = 38.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header and buttons share one item so focusing a button keeps the title in view
            item {
                Column(Modifier.heightIn(min = 250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Header(item)
                    Spacer(Modifier.height(4.dp))
                    ActionRow(
                        item = item,
                        vm = vm,
                        playFocus = playFocus,
                        onCast = cast,
                        onIdentify = { onIdentify(IdentifyTarget(itemId = item.id, kind = item.kind, query = item.title)) }
                    )
                }
            }
            if (item.isSeries) {
                if (item.seasons.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.offset(x = (-6).dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp).focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                item.seasons.firstOrNull { it.number == state.season }?.let { season ->
                    item(key = "season-${season.number}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                (if (item.seasons.size == 1) "Episodios" else season.name ?: "Temporada ${season.number}"),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "${season.episodesWatched} de ${season.episodes.size} vistos",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceMuted
                            )
                            Spacer(Modifier.weight(1f))
                            IconPill(Icons.Outlined.Visibility, "Temporada vista o no vista", { seasonMenu = season })
                        }
                    }
                }
                items(state.episodes, key = { "${it.season}-${it.number}" }) { episode ->
                    val key = "${episode.season}-${episode.number}"
                    EpisodeRow(
                        episode = episode,
                        stillUrl = urls.libraryImage(episode.stillUrl, "w300"),
                        isNext = item.nextUp?.let { it.season == episode.season && it.number == episode.number } == true,
                        onClick = {
                            vm.episodeFile(episode)?.let { (file, start) ->
                                vm.lastFocus = key
                                vm.play(file, start)
                            } ?: vm.say("Este episodio no tiene ningún fichero")
                        },
                        onLongClick = {
                            vm.lastFocus = key
                            episodeMenu = episode
                        },
                        modifier = Modifier.returnFocus(back, key)
                    )
                }
            } else if (item.files.size > 1) {
                item { Text("Versiones", style = MaterialTheme.typography.titleLarge) }
                items(item.files, key = { "${it.channelId}-${it.fileId}" }) { file ->
                    val key = "${file.channelId}-${file.fileId}"
                    VersionRow(
                        file = file,
                        onClick = {
                            vm.lastFocus = key
                            vm.play(file, file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L)
                        },
                        onLongClick = { fileMenu = file },
                        modifier = Modifier.returnFocus(back, key)
                    )
                }
            }
        }
        MessageBar(state.message, vm::messageShown)
    }

    episodeMenu?.let { episode ->
        val (file, start) = vm.episodeFile(episode) ?: (null to 0L)
        ActionMenu(
            title = "T${episode.season} E${episode.number}" + (episode.title?.let { " · $it" } ?: ""),
            subtitle = file?.fileName,
            onDismiss = { episodeMenu = null },
            actions = buildList {
                if (file != null) {
                    if (start > 0) add(MenuAction("Continuar (${Format.duration(start)})", Icons.Filled.PlayArrow) { vm.play(file, start) })
                    add(MenuAction(if (start > 0) "Ver desde el principio" else "Reproducir", if (start > 0) Icons.Outlined.Replay else Icons.Filled.PlayArrow) { vm.play(file, 0L) })
                    add(watchedAction(episode.completed) { vm.setWatched(file, it) })
                    add(MenuAction("Enviar a la tele", Icons.Outlined.Cast) { cast(file, start) })
                    // Several copies of the same episode: the first is the one played by default.
                    episode.files.drop(1).forEach { other ->
                        add(MenuAction("Otra copia: ${other.fileName}", Icons.Outlined.Movie) { vm.play(other, other.watch?.takeIf { it.inProgress }?.positionMs ?: 0L) })
                    }
                    add(MenuAction("Corregir identificación", Icons.Outlined.Edit) {
                        onIdentify(file.identifyTarget().copy(kind = item.kind, query = item.title, season = episode.season, episode = episode.number))
                    })
                }
            }
        )
    }
    fileMenu?.let { file ->
        LibraryMenu(
            target = LibraryMenuTarget.File(file),
            onDismiss = { fileMenu = null },
            onPlay = { f, start -> vm.play(f, start) },
            onCast = { f -> cast(f, f.watch?.takeIf { it.inProgress }?.positionMs ?: 0L) },
            onFileWatched = vm::setWatched,
            onIdentify = onIdentify
        )
    }
    seasonMenu?.let { season ->
        ActionMenu(
            title = season.name ?: "Temporada ${season.number}",
            subtitle = "${season.episodesWatched} de ${season.episodes.size} vistos",
            onDismiss = { seasonMenu = null },
            actions = listOf(
                MenuAction("Marcar la temporada como vista", Icons.Outlined.Visibility) { vm.setSeasonWatched(season.number, true) },
                MenuAction("Marcar la temporada como no vista", Icons.Outlined.VisibilityOff) { vm.setSeasonWatched(season.number, false) }
            )
        )
    }
    CastSheet(castVm, onOpenPanel = onOpenCastPanel)
}

private fun watchedAction(watched: Boolean, set: (Boolean) -> Unit) = MenuAction(
    if (watched) "Marcar como no visto" else "Marcar como visto",
    if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
) { set(!watched) }

@Composable
private fun Header(item: LibraryItemDetailDto) {
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            if (item.isSeries) "SERIE" else "PELÍCULA",
            style = MaterialTheme.typography.labelMedium,
            color = palette.accent
        )
        Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!item.originalTitle.isNullOrBlank() && !item.originalTitle.equals(item.title, ignoreCase = true)) {
            Text(item.originalTitle, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item.rating?.takeIf { it > 0 }?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Star, modifier = Modifier.size(16.dp))
                    Text("%.1f".format(rating), style = MaterialTheme.typography.titleSmall)
                }
            }
            val meta = listOfNotNull(
                item.year?.toString(),
                item.runtime?.takeIf { it > 0 }?.let { Format.minutes(it) },
                if (item.isSeries && item.seasonCount > 0) (if (item.seasonCount == 1) "1 temporada" else "${item.seasonCount} temporadas") else null,
                if (item.isSeries && item.watch.episodesTotal > 0) "${item.watch.episodesWatched} de ${item.watch.episodesTotal} vistos" else null
            ).joinToString("  ·  ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            if (item.watch.completed) Badge("Vista", strong = true)
        }
        if (item.genres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.genres.take(4).forEach { genre ->
                    Text(
                        genre,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        item.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = palette.accentEnd, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.overview?.takeIf { it.isNotBlank() }?.let { Overview(it) }
        item.resume?.watch?.takeIf { it.inProgress }?.let { watch ->
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProgressBar(watch.progress.toFloat(), Modifier.width(200.dp))
                Format.remaining(watch.positionMs, watch.durationMs)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                }
            }
        }
    }
}

/** The synopsis is focusable so a long one can be expanded with the D-pad. */
@Composable
private fun Overview(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val body: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier.animateContentSize()
        )
    }
    // One that fits is not worth a stop of the D-pad.
    if (!overflows && !expanded) {
        Box(Modifier.padding(vertical = 2.dp)) { body() }
        return
    }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.tapClick { expanded = !expanded },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            focusedContentColor = Color.White
        )
    ) {
        Box(Modifier.padding(vertical = 2.dp)) { body() }
    }
}

@Composable
private fun ActionRow(
    item: LibraryItemDetailDto,
    vm: LibraryItemViewModel,
    playFocus: FocusRequester,
    onCast: (LibraryFileDto, Long) -> Unit,
    onIdentify: () -> Unit
) {
    val primary = vm.primaryFile()
    val resume = item.resume
    Row(
        modifier = Modifier.offset(x = (-6).dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resuming = resume != null && (resume.watch?.positionMs ?: 0L) > 0
        val playLabel = when {
            resuming && item.isSeries && resume?.episode != null -> "Continuar T${resume.season ?: 1} E${resume.episode}"
            resuming -> "Continuar (${Format.duration(resume!!.watch!!.positionMs)})"
            item.isSeries && item.nextUp != null -> "Reproducir T${item.nextUp.season} E${item.nextUp.number}"
            else -> "Reproducir"
        }
        PillButton(
            label = playLabel,
            icon = Icons.Filled.PlayArrow,
            enabled = primary != null,
            primary = true,
            onClick = {
                vm.lastFocus = null
                primary?.let { (file, start) -> vm.play(file, start) }
            },
            modifier = Modifier.focusRequester(playFocus)
        )
        if (resuming) {
            IconPill(Icons.Outlined.Replay, "Desde el principio", {
                vm.lastFocus = null
                primary?.let { (file, _) -> vm.play(file, 0L) }
            })
        }
        val watched = item.watch.completed
        IconPill(
            icon = if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            label = if (watched) "Marcar como no vista" else "Marcar como vista",
            onClick = vm::toggleWatched
        )
        IconPill(Icons.Outlined.Cast, "Enviar a la tele", { primary?.let { (file, start) -> onCast(file, start) } }, enabled = primary != null)
        IconPill(Icons.Outlined.Edit, "Corregir identificación", onIdentify)
        IconPill(Icons.Outlined.Sync, "Actualizar datos", vm::refreshMetadata)
    }
}

@Composable
private fun EpisodeRow(
    episode: LibraryEpisodeDto,
    stillUrl: String?,
    isNext: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = episode.watch?.takeIf { it.inProgress }?.progress?.toFloat()
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        border = listRowBorder(16.dp),
        colors = listRowColors(isNext)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(150.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Art(stillUrl, "${episode.season}x${episode.number} ${episode.title}", Icons.Outlined.Tv, iconSize = 26.dp)
                episode.runtime?.takeIf { it > 0 }?.let {
                    Badge("$it min", Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = if (progress != null) 14.dp else 6.dp))
                }
                if (progress != null) {
                    ProgressBar(progress, Modifier.align(Alignment.BottomCenter).padding(6.dp), height = 3.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${episode.number}. ${episode.title?.takeIf { it.isNotBlank() } ?: "Episodio ${episode.number}"}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNext) Badge("Siguiente")
                }
                val meta = listOfNotNull(
                    episode.airDate?.let { Format.day(it) }?.takeIf { it.isNotBlank() },
                    episode.watch?.takeIf { it.inProgress }?.let { Format.remaining(it.positionMs, it.durationMs) },
                    episode.files.size.takeIf { it > 1 }?.let { "$it copias" },
                    if (episode.files.isEmpty()) "Sin fichero" else null
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1)
                }
                episode.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (episode.completed) {
                Icon(Icons.Filled.Check, contentDescription = "Visto", modifier = Modifier.padding(end = 8.dp).size(20.dp))
            }
        }
    }
}

@Composable
private fun VersionRow(file: LibraryFileDto, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        border = listRowBorder(),
        colors = listRowColors()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(file.sizeText ?: Format.bytes(file.size), style = MaterialTheme.typography.bodySmall)
                if (file.watch?.completed == true) Icon(Icons.Filled.Check, contentDescription = "Vista", modifier = Modifier.size(18.dp))
            }
            file.watch?.takeIf { it.inProgress }?.let {
                Spacer(Modifier.height(7.dp))
                ProgressBar(it.progress.toFloat())
            }
        }
    }
}

private val SIDE = 56.dp
