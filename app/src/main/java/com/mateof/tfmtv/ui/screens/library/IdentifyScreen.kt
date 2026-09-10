package com.mateof.tfmtv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.data.model.ProviderCandidateDto
import com.mateof.tfmtv.ui.components.EmptyState
import com.mateof.tfmtv.ui.components.Loading
import com.mateof.tfmtv.ui.components.PosterImage
import com.mateof.tfmtv.ui.components.SearchField
import com.mateof.tfmtv.ui.components.TabChip
import com.mateof.tfmtv.ui.components.dpadEscape
import com.mateof.tfmtv.ui.components.rememberMediaUrls
import com.mateof.tfmtv.ui.components.tapClick
import com.mateof.tfmtv.ui.theme.Background
import kotlinx.coroutines.delay

/** Manual identification of a file or a whole item. A screen, not a dialog: focus stays simple. */
@Composable
fun IdentifyScreen(target: IdentifyTarget, onDone: () -> Unit) {
    val vm: IdentifyViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(target) { vm.start(target) }
    LaunchedEffect(Unit) { vm.done.collect { onDone() } }

    // Compose would focus the text field first, which pops the TV keyboard over
    // the results; land on the first result (or the kind chips) instead.
    val firstResultFocus = remember { FocusRequester() }
    val chipFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.results.size, state.searching, state.searched) {
        // Only once, after the first search: later searches come from typing
        // and must not steal the focus from the text field.
        if (initialFocusDone || state.searching || !state.searched) return@LaunchedEffect
        delay(80)
        initialFocusDone = true
        runCatching { if (state.results.isNotEmpty()) firstResultFocus.requestFocus() else chipFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(start = 48.dp, end = 48.dp, top = 28.dp)) {
        Text("Corregir identificación", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (state.isFile) "Se aplicará sólo a este fichero" else "Se aplicará a todos los ficheros del título",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = "Título o id de IMDb (tt…)",
                modifier = Modifier.width(420.dp)
            )
            TabChip(
                "Película",
                selected = state.kind == LibraryKind.MOVIE,
                onClick = { vm.setKind(LibraryKind.MOVIE) },
                modifier = Modifier.focusRequester(chipFocus)
            )
            TabChip("Serie", selected = state.kind == LibraryKind.SERIES, onClick = { vm.setKind(LibraryKind.SERIES) })
            if (state.needsEpisode) {
                OutlinedTextField(
                    value = state.season,
                    onValueChange = vm::setSeason,
                    label = { androidx.compose.material3.Text("Temporada") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(130.dp).dpadEscape(focusManager)
                )
                OutlinedTextField(
                    value = state.episode,
                    onValueChange = vm::setEpisode,
                    label = { androidx.compose.material3.Text("Episodio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(130.dp).dpadEscape(focusManager)
                )
            }
        }

        if (state.isFile) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::ignore, enabled = !state.busy, modifier = Modifier.tapClick(vm::ignore)) {
                    Text("Ignorar este fichero")
                }
            }
        }

        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Box(Modifier.fillMaxSize().padding(top = 12.dp)) {
            when {
                state.busy || state.searching -> Loading()
                state.results.isEmpty() -> EmptyState(
                    if (state.searched) "Ningún resultado para «${state.query}». Prueba con otro título o el id de IMDb."
                    else "Escribe un título para buscar."
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.results, key = { _, c -> "${c.provider}-${c.providerId}-${c.kind}" }) { index, candidate ->
                        CandidateRow(
                            candidate,
                            onClick = { vm.choose(candidate) },
                            modifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: ProviderCandidateDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val urls = rememberMediaUrls()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
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
                    .width(64.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PosterImage(
                    urls.absolute(candidate.posterUrl),
                    if (candidate.kind == LibraryKind.SERIES) Icons.Outlined.Tv else Icons.Outlined.Movie
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = listOfNotNull(candidate.title, candidate.year?.let { "($it)" }).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    if (candidate.kind == LibraryKind.SERIES) "Serie" else "Película",
                    candidate.originalTitle?.takeIf { !it.equals(candidate.title, ignoreCase = true) },
                    candidate.provider.uppercase(),
                    candidate.imdbId
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                candidate.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
