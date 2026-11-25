package com.mateof.tfmtv.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.ui.components.ActionMenu
import com.mateof.tfmtv.ui.components.MenuAction

/** What a long press was made on. */
sealed interface LibraryMenuTarget {
    data class File(val file: LibraryFileDto) : LibraryMenuTarget
    data class Item(val item: LibraryItemDto) : LibraryMenuTarget
}

/** The menu of a title or of a file of the library; entries without a callback are left out. */
@Composable
fun LibraryMenu(
    target: LibraryMenuTarget,
    onDismiss: () -> Unit,
    onPlay: ((LibraryFileDto, Long) -> Unit)? = null,
    onOpen: ((String) -> Unit)? = null,
    onCast: ((LibraryFileDto) -> Unit)? = null,
    onIdentify: ((IdentifyTarget) -> Unit)? = null,
    onFileWatched: ((LibraryFileDto, Boolean) -> Unit)? = null,
    onItemWatched: ((LibraryItemDto, Boolean) -> Unit)? = null,
    onForget: ((LibraryFileDto) -> Unit)? = null
) {
    when (target) {
        is LibraryMenuTarget.File -> {
            val file = target.file
            val position = file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L
            val watched = file.watch?.completed == true
            ActionMenu(
                title = file.displayTitle(),
                subtitle = file.fileName,
                onDismiss = onDismiss,
                actions = buildList {
                    onPlay?.let { play ->
                        if (position > 0) {
                            add(MenuAction("Continuar (${Format.duration(position)})", Icons.Outlined.PlayArrow) { play(file, position) })
                            add(MenuAction("Ver desde el principio", Icons.Outlined.Replay) { play(file, 0L) })
                        } else add(MenuAction("Reproducir", Icons.Outlined.PlayArrow) { play(file, 0L) })
                    }
                    val itemId = file.itemId
                    if (onOpen != null && itemId != null) add(MenuAction("Ver ficha", Icons.Outlined.Info) { onOpen(itemId) })
                    onFileWatched?.let { set ->
                        add(
                            MenuAction(
                                if (watched) "Marcar como no visto" else "Marcar como visto",
                                if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                            ) { set(file, !watched) }
                        )
                    }
                    if (onForget != null && position > 0) {
                        add(MenuAction("Quitar de «Continuar»", Icons.Outlined.RemoveCircleOutline) { onForget(file) })
                    }
                    onCast?.let { cast -> add(MenuAction("Enviar a la tele", Icons.Outlined.Cast) { cast(file) }) }
                    onIdentify?.let { identify ->
                        add(MenuAction("Corregir identificación", Icons.Outlined.Edit) { identify(file.identifyTarget()) })
                    }
                }
            )
        }

        is LibraryMenuTarget.Item -> {
            val item = target.item
            ActionMenu(
                title = item.title,
                subtitle = listOfNotNull(item.year?.toString(), item.genres.firstOrNull()).joinToString(" · "),
                onDismiss = onDismiss,
                actions = buildList {
                    onOpen?.let { open -> add(MenuAction("Ver ficha", Icons.Outlined.Info) { open(item.id) }) }
                    onItemWatched?.let { set ->
                        val watched = item.watch.completed
                        add(
                            MenuAction(
                                if (watched) "Marcar como no vista" else "Marcar como vista",
                                if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                            ) { set(item, !watched) }
                        )
                    }
                    onIdentify?.let { identify ->
                        add(MenuAction("Corregir identificación", Icons.Outlined.Edit) {
                            identify(IdentifyTarget(itemId = item.id, kind = item.kind, query = item.title))
                        })
                    }
                }
            )
        }
    }
}
