package com.mateof.tfmtv.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.repo.MediaUrls
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.cast.CastSheet
import com.mateof.tfmtv.ui.cast.CastTarget
import com.mateof.tfmtv.ui.cast.CastViewModel
import com.mateof.tfmtv.ui.components.ChannelTile
import com.mateof.tfmtv.ui.components.Clock
import com.mateof.tfmtv.ui.components.ContentEnd
import com.mateof.tfmtv.ui.components.ContentStart
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.ErrorState
import com.mateof.tfmtv.ui.components.HeroBackdrop
import com.mateof.tfmtv.ui.components.MediaRow
import com.mateof.tfmtv.ui.components.MessageBar
import com.mateof.tfmtv.ui.components.PivotRows
import com.mateof.tfmtv.ui.components.PosterTile
import com.mateof.tfmtv.ui.components.Skeleton
import com.mateof.tfmtv.ui.components.SkeletonRow
import com.mateof.tfmtv.ui.components.WideTile
import com.mateof.tfmtv.ui.components.entryFocus
import com.mateof.tfmtv.ui.components.rememberFocusReturn
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.returnFocus
import com.mateof.tfmtv.ui.components.thumbnailOf
import com.mateof.tfmtv.ui.screens.library.IdentifyTarget
import com.mateof.tfmtv.ui.screens.library.LibraryMenu
import com.mateof.tfmtv.ui.screens.library.LibraryMenuTarget
import com.mateof.tfmtv.ui.screens.library.displayTitle
import com.mateof.tfmtv.ui.theme.OnSurfaceMuted
import com.mateof.tfmtv.ui.theme.palette

/** What the top of the screen says about the card with the focus. */
private data class Hero(
    val key: String,
    val kicker: String,
    val title: String,
    val meta: String,
    val overview: String? = null,
    val backdrop: String? = null
)

@Composable
fun StartContent(
    favorites: List<ChannelDto>,
    ownChannels: List<ChannelDto>,
    onChannel: (Long, String) -> Unit,
    onChannelMenu: (ChannelDto) -> Unit,
    onOpenItem: (String) -> Unit,
    onIdentify: (IdentifyTarget) -> Unit,
    onPlayInternal: (PlayEvent.Internal) -> Unit,
    onOpenCastPanel: () -> Unit
) {
    val vm: StartViewModel = hiltViewModel()
    val castVm: CastViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val urls = rememberMediaUrls()
    var focused by remember { mutableStateOf<Hero?>(null) }
    var menu by remember { mutableStateOf<LibraryMenuTarget?>(null) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayEvent.Internal -> onPlayInternal(event)
                is PlayEvent.Failed -> vm.say(event.message)
                PlayEvent.Handed -> Unit
            }
        }
    }

    // Without favourites the own channels are the closest thing to them.
    val channels = favorites.ifEmpty { ownChannels }
    val empty = state.continueFiles.isEmpty() && state.nextSeries.isEmpty() && state.recentMovies.isEmpty() &&
        state.recentSeries.isEmpty() && channels.isEmpty()

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> StartSkeleton()
            state.error != null && empty -> ErrorState(state.error!!, onRetry = vm::load)
            empty -> EmptyState("Aquí aparecerá lo que dejes a medias, lo último que llegue a la biblioteca y tus canales favoritos.")
            else -> {
                val first = remember(state.continueFiles, state.recentMovies) {
                    state.continueFiles.firstOrNull()?.hero(urls) ?: state.recentMovies.firstOrNull()?.hero(urls, "Película añadida")
                }
                val hero = focused ?: first
                HeroBackdrop(hero?.backdrop)
                Column(Modifier.fillMaxSize()) {
                    HeroText(hero)
                    Rows(
                        state = state,
                        vm = vm,
                        urls = urls,
                        channels = channels,
                        favorites = favorites.isNotEmpty(),
                        onHero = { focused = it },
                        onMenu = { menu = it },
                        onChannel = onChannel,
                        onChannelMenu = onChannelMenu,
                        onOpenItem = onOpenItem
                    )
                }
            }
        }
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = ContentEnd))
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

@Composable
private fun HeroText(hero: Hero?) {
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT).padding(start = ContentStart, top = 26.dp, end = ContentEnd)) {
        val effects = palette.effects
        AnimatedContent(
            targetState = hero,
            contentKey = { it?.key },
            transitionSpec = {
                if (effects) (fadeIn(tween(320, 120)) + slideInVertically(tween(320, 120)) { it / 14 }) togetherWith fadeOut(tween(120))
                else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            },
            label = "hero"
        ) { current ->
            if (current != null) {
                Column(Modifier.widthIn(max = 500.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.kicker.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.accent)
                    Text(current.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (current.meta.isNotBlank()) {
                        Text(current.meta, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    current.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Rows(
    state: StartState,
    vm: StartViewModel,
    urls: MediaUrls,
    channels: List<ChannelDto>,
    favorites: Boolean,
    onHero: (Hero) -> Unit,
    onMenu: (LibraryMenuTarget) -> Unit,
    onChannel: (Long, String) -> Unit,
    onChannelMenu: (ChannelDto) -> Unit,
    onOpenItem: (String) -> Unit
) {
    val back = rememberFocusReturn(vm.lastFocus)
    val listState = rememberLazyListState()
    val openItem: (String, String) -> Unit = { row, id ->
        vm.lastFocus = "$row/$id"
        onOpenItem(id)
    }
    val firstRow = when {
        state.continueFiles.isNotEmpty() -> "continue"
        state.nextSeries.isNotEmpty() -> "next"
        state.recentMovies.isNotEmpty() -> "movies"
        state.recentSeries.isNotEmpty() -> "series"
        else -> "channels"
    }

    PivotRows(top = ROW_TITLE) { rowSpec ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.continueFiles.isNotEmpty()) item(key = "continue") {
                MediaRow("Continuar viendo", spec = rowSpec) {
                    itemsIndexed(state.continueFiles, key = { _, f -> fileKey(f) }) { index, file ->
                        val item = file.item
                        WideTile(
                            title = item?.title ?: file.fileName,
                            subtitle = listOfNotNull(
                                file.episodeLabel(),
                                file.watch?.let { Format.remaining(it.positionMs, it.durationMs) }
                            ).joinToString(" · "),
                            imageUrl = urls.libraryImage(item?.backdropUrl, "w500") ?: thumbnailOf(file.file),
                            progress = file.watch?.progress?.toFloat(),
                            placeholder = if (item?.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie,
                            onClick = {
                                vm.lastFocus = "continue/${fileKey(file)}"
                                vm.play(file)
                            },
                            onLongClick = { onMenu(LibraryMenuTarget.File(file)) },
                            onFocused = { onHero(file.hero(urls)) },
                            modifier = Modifier.width(204.dp)
                                .entryFocus(firstRow == "continue" && index == 0)
                                .returnFocus(back, "continue/${fileKey(file)}")
                        )
                    }
                }
            }
            if (state.nextSeries.isNotEmpty()) item(key = "next") {
                MediaRow("Siguiente episodio", spec = rowSpec) {
                    itemsIndexed(state.nextSeries, key = { _, i -> i.id }) { index, item ->
                        WideTile(
                            title = item.title,
                            subtitle = "${item.watch.episodesWatched} de ${item.watch.episodesTotal} vistos",
                            imageUrl = urls.libraryImage(item.backdropUrl, "w500"),
                            progress = item.watch.episodesWatched / item.watch.episodesTotal.coerceAtLeast(1).toFloat(),
                            placeholder = Icons.Outlined.Tv,
                            onClick = { openItem("next", item.id) },
                            onLongClick = { onMenu(LibraryMenuTarget.Item(item)) },
                            onFocused = { onHero(item.hero(urls, "Sigue con la serie")) },
                            modifier = Modifier.width(204.dp)
                                .entryFocus(firstRow == "next" && index == 0)
                                .returnFocus(back, "next/${item.id}")
                        )
                    }
                }
            }
            posters("movies", "Películas añadidas", "Película añadida", state.recentMovies)?.let { row ->
                item(key = row.key) { PosterRow(row, rowSpec, urls, firstRow, back, onHero, onMenu, openItem) }
            }
            posters("series", "Series añadidas", "Serie añadida", state.recentSeries)?.let { row ->
                item(key = row.key) { PosterRow(row, rowSpec, urls, firstRow, back, onHero, onMenu, openItem) }
            }
            posters("top", "Mejor valoradas sin ver", "Sin ver", state.topMovies)?.let { row ->
                item(key = row.key) { PosterRow(row, rowSpec, urls, firstRow, back, onHero, onMenu, openItem) }
            }
            if (channels.isNotEmpty()) item(key = "channels") {
                MediaRow(if (favorites) "Canales favoritos" else "Mis canales", spec = rowSpec) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                        ChannelTile(
                            channel = channel,
                            onClick = {
                                vm.lastFocus = "channels/${channel.id}"
                                onChannel(channel.id, channel.name.orEmpty())
                            },
                            onLongClick = { onChannelMenu(channel) },
                            onFocused = {
                                onHero(
                                    Hero(
                                        key = "channel-${channel.id}",
                                        kicker = if (channel.isFavorite) "Canal favorito" else "Canal",
                                        title = channel.name.orEmpty(),
                                        meta = listOfNotNull(
                                            channel.videoCount?.let { if (it == 1L) "1 vídeo" else "$it vídeos" },
                                            if (channel.isOwner) "Propio" else "Compartido"
                                        ).joinToString("  ·  ")
                                    )
                                )
                            },
                            modifier = Modifier.width(250.dp)
                                .entryFocus(firstRow == "channels" && index == 0)
                                .returnFocus(back, "channels/${channel.id}")
                        )
                    }
                }
            }
        }
    }
}

private class Posters(val key: String, val title: String, val kicker: String, val items: List<LibraryItemDto>)

private fun posters(key: String, title: String, kicker: String, items: List<LibraryItemDto>): Posters? =
    if (items.isEmpty()) null else Posters(key, title, kicker, items)

@Composable
private fun PosterRow(
    row: Posters,
    spec: androidx.compose.foundation.gestures.BringIntoViewSpec,
    urls: MediaUrls,
    firstRow: String,
    back: com.mateof.tfmtv.ui.components.FocusReturn,
    onHero: (Hero) -> Unit,
    onMenu: (LibraryMenuTarget) -> Unit,
    onOpen: (String, String) -> Unit
) {
    MediaRow(row.title, spec = spec) {
        itemsIndexed(row.items, key = { _, i -> i.id }) { index, item ->
            PosterTile(
                title = item.title,
                subtitle = item.year?.toString(),
                imageUrl = urls.libraryImage(item.posterUrl, "w342"),
                progress = if (item.watch.inProgress) item.watch.progress.toFloat() else null,
                completed = item.watch.completed,
                rating = item.rating,
                placeholder = if (item.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie,
                onClick = { onOpen(row.key, item.id) },
                onLongClick = { onMenu(LibraryMenuTarget.Item(item)) },
                onFocused = { onHero(item.hero(urls, row.kicker)) },
                modifier = Modifier.width(118.dp)
                    .entryFocus(firstRow == row.key && index == 0)
                    .returnFocus(back, "${row.key}/${item.id}")
            )
        }
    }
}

@Composable
private fun StartSkeleton() {
    Column(Modifier.fillMaxSize().padding(start = ContentStart, top = 34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Skeleton(Modifier.width(110.dp).height(12.dp), corner = 6.dp)
        Skeleton(Modifier.width(340.dp).height(38.dp), corner = 10.dp)
        Skeleton(Modifier.width(260.dp).height(14.dp), corner = 6.dp)
        Skeleton(Modifier.width(420.dp).height(14.dp), corner = 6.dp)
        SkeletonRow(cardWidth = 204.dp, ratio = 16f / 9f, modifier = Modifier.padding(top = 54.dp))
        SkeletonRow(cardWidth = 118.dp, ratio = 2f / 3f, modifier = Modifier.padding(top = 40.dp), count = 8)
    }
}

private fun fileKey(file: LibraryFileDto) = "${file.channelId}-${file.fileId}"

private fun LibraryFileDto.episodeLabel(): String? =
    if (item?.kind == LibraryKind.SERIES && episode != null) "T${season ?: 1} E$episode" else null

private fun LibraryFileDto.hero(urls: MediaUrls) = Hero(
    key = "file-$channelId-$fileId",
    kicker = "Continuar viendo",
    title = item?.title ?: fileName,
    meta = listOfNotNull(
        episodeLabel(),
        item?.year?.toString(),
        watch?.let { Format.remaining(it.positionMs, it.durationMs) },
        if (item == null) sizeText else null
    ).joinToString("  ·  "),
    overview = item?.overview,
    backdrop = urls.libraryImage(item?.backdropUrl, "w1280")
)

private fun LibraryItemDto.hero(urls: MediaUrls, kicker: String) = Hero(
    key = "item-$id",
    kicker = kicker,
    title = title,
    meta = listOfNotNull(
        year?.toString(),
        rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
        if (kind == LibraryKind.SERIES) seasonCount.takeIf { it > 0 }?.let { if (it == 1) "1 temporada" else "$it temporadas" }
        else runtime?.takeIf { it > 0 }?.let { Format.minutes(it) },
        genres.take(2).joinToString(", ").takeIf { it.isNotBlank() }
    ).joinToString("  ·  "),
    overview = overview,
    backdrop = urls.libraryImage(backdropUrl, "w1280")
)

private val HERO_HEIGHT = 222.dp
private val ROW_TITLE = 34.dp
