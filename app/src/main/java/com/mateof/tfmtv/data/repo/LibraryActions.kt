package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.userMessage
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import javax.inject.Inject
import javax.inject.Singleton

/** What the menus of the cards do. Each answers with the text to show. */
@Singleton
class LibraryActions @Inject constructor(
    private val library: LibraryRepository,
    private val watch: WatchRepository
) {
    suspend fun setWatched(file: LibraryFileDto, watched: Boolean): Result<String> =
        reporting(if (watched) "Marcado como visto" else "Marcado como no visto") {
            watch.setWatched(file.channelId, file.fileId, watched)
        }

    suspend fun setWatched(item: LibraryItemDto, watched: Boolean): Result<String> =
        reporting(if (watched) "«${item.title}» marcada como vista" else "«${item.title}» marcada como no vista") {
            library.setItemWatched(item.id, watched)
        }

    suspend fun forget(file: LibraryFileDto): Result<String> =
        reporting("Quitado de «Continuar»") { watch.forget(file.channelId, file.fileId) }

    private suspend fun reporting(done: String, block: suspend () -> Unit): Result<String> =
        runCatching { block() }.fold(
            onSuccess = { Result.success(done) },
            onFailure = { Result.failure(Exception("No se pudo: ${it.userMessage()}")) }
        )
}
