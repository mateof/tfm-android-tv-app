package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.ApiException
import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.core.apiCallNullable
import com.mateof.tfmtv.core.apiCallPaged
import com.mateof.tfmtv.data.api.LibraryApi
import com.mateof.tfmtv.data.model.IdentifyItemRequest
import com.mateof.tfmtv.data.model.ItemWatchedRequest
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDetailDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryScanRequest
import com.mateof.tfmtv.data.model.LibraryScanStateDto
import com.mateof.tfmtv.data.model.LibraryStatsDto
import com.mateof.tfmtv.data.model.MatchFileRequest
import com.mateof.tfmtv.data.model.ProviderCandidateDto
import com.mateof.tfmtv.data.prefs.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Server answers for a library that is switched off, or a server too old to have one. */
object LibraryErrors {
    const val DISABLED = "library_disabled"

    const val DISABLED_MESSAGE =
        "La biblioteca está desactivada en el servidor. Actívala y añade la clave de un proveedor (TMDB u OMDb) en los ajustes de la web."
    const val MISSING_MESSAGE = "El servidor no tiene biblioteca. Actualízalo a la última versión."

    /** A message for the two "not available" cases, null for any other error. */
    fun unavailableMessage(e: Throwable): String? = when {
        e !is ApiException -> null
        e.code == DISABLED -> DISABLED_MESSAGE
        e.httpStatus == 404 -> MISSING_MESSAGE
        else -> null
    }
}

@Singleton
class LibraryRepository @Inject constructor(
    private val api: LibraryApi,
    private val prefs: ServerPreferences
) {
    /** The library leaves out what lives in hidden channels only, unless the setting shows them. */
    private val hidden: Boolean get() = prefs.showHiddenChannels.value

    suspend fun stats(): LibraryStatsDto = withContext(Dispatchers.IO) { apiCall { api.stats() } }

    /** Every item of one kind, page by page, like the channel list. */
    suspend fun items(kind: String): List<LibraryItemDto> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LibraryItemDto>()
        var page = 1
        while (page <= MAX_PAGES) {
            val paged = apiCallPaged { api.items(kind = kind, includeHidden = hidden, page = page, pageSize = PAGE_SIZE) }
            out += paged.items
            if (paged.page?.hasNext != true) break
            page++
        }
        out
    }

    /** The first titles in the order of the server: best rated, last played… */
    suspend fun top(kind: String, sortBy: String, limit: Int): List<LibraryItemDto> = withContext(Dispatchers.IO) {
        apiCall { api.items(kind = kind, includeHidden = hidden, sortBy = sortBy, sortDescending = true, pageSize = limit) }
    }

    suspend fun recent(kind: String, limit: Int): List<LibraryItemDto> = withContext(Dispatchers.IO) {
        apiCall { api.recent(kind = kind, limit = limit, includeHidden = hidden) }
    }

    suspend fun search(text: String, limit: Int = 40): List<LibraryItemDto> = withContext(Dispatchers.IO) {
        apiCall { api.items(search = text, includeHidden = hidden, sortBy = "title", pageSize = limit) }
    }

    suspend fun history(): List<LibraryFileDto> = withContext(Dispatchers.IO) {
        apiCall { api.history(includeHidden = hidden) }
    }

    suspend fun file(channelId: Long, fileId: String): LibraryFileDto? = withContext(Dispatchers.IO) {
        apiCallNullable { api.file(channelId, fileId) }
    }

    suspend fun refreshItem(id: String) {
        withContext(Dispatchers.IO) { apiCallNullable { api.refreshItem(id) } }
    }

    suspend fun cancelScan() {
        withContext(Dispatchers.IO) { apiCallNullable { api.cancelScan() } }
    }

    suspend fun item(id: String): LibraryItemDetailDto = withContext(Dispatchers.IO) {
        apiCall { api.item(id) }
    }

    suspend fun continueWatching(): List<LibraryFileDto> = withContext(Dispatchers.IO) {
        apiCall { api.continueWatching(includeHidden = hidden) }
    }

    suspend fun files(status: String): List<LibraryFileDto> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LibraryFileDto>()
        var page = 1
        while (page <= MAX_PAGES) {
            val paged = apiCallPaged { api.files(status = status, page = page, pageSize = PAGE_SIZE) }
            out += paged.items
            if (paged.page?.hasNext != true) break
            page++
        }
        out
    }

    suspend fun searchProviders(q: String, kind: String?): List<ProviderCandidateDto> =
        withContext(Dispatchers.IO) {
            val imdb = q.trim().takeIf { it.matches(Regex("tt\\d{5,}")) }
            apiCall { api.searchProviders(q = if (imdb == null) q.trim() else null, kind = kind, imdbId = imdb) }
        }

    suspend fun matchFile(channelId: Long, fileId: String, body: MatchFileRequest): LibraryFileDto? =
        withContext(Dispatchers.IO) { apiCallNullable { api.matchFile(channelId, fileId, body) } }

    suspend fun ignoreFile(channelId: Long, fileId: String): LibraryFileDto? =
        withContext(Dispatchers.IO) { apiCallNullable { api.ignoreFile(channelId, fileId) } }

    suspend fun identifyItem(id: String, body: IdentifyItemRequest): LibraryItemDetailDto? =
        withContext(Dispatchers.IO) { apiCallNullable { api.identifyItem(id, body) } }

    /** [season] null is the whole item. */
    suspend fun setItemWatched(id: String, watched: Boolean, season: Int? = null): LibraryItemDetailDto? =
        withContext(Dispatchers.IO) {
            apiCallNullable {
                if (watched) api.markItemWatched(id, ItemWatchedRequest(season)) else api.markItemUnwatched(id, season)
            }
        }

    /** Starts a scan of every indexed channel; false if one was already running. */
    suspend fun startScan(): Boolean = withContext(Dispatchers.IO) {
        try {
            apiCallNullable { api.scan(LibraryScanRequest()) }
            true
        } catch (e: ApiException) {
            if (e.code == "already_running") false else throw e
        }
    }

    suspend fun scanState(): LibraryScanStateDto? = withContext(Dispatchers.IO) {
        apiCallNullable { api.scanState() }
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 20
    }
}
