package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.ApiException
import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.core.apiCallNullable
import com.mateof.tfmtv.core.apiCallPaged
import com.mateof.tfmtv.data.api.ChannelsApi
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChannelFoldersDto
import com.mateof.tfmtv.data.model.ChannelRefreshRequest
import com.mateof.tfmtv.data.model.ChannelRefreshResultDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelsRepository @Inject constructor(
    private val api: ChannelsApi
) {
    /**
     * Every chat, page by page. The sections and the search box filter this list
     * on the client, so a partial list silently hides channels. [includeHidden]
     * overrides the server's own "show hidden channels" setting.
     */
    suspend fun all(includeHidden: Boolean = false): List<ChannelDto> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ChannelDto>()
        var page = 1
        while (page <= MAX_PAGES) {
            val paged = apiCallPaged {
                api.list(
                    includeHidden = includeHidden,
                    sortBy = "name",
                    page = page,
                    pageSize = PAGE_SIZE
                )
            }
            out += paged.items
            if (paged.page?.hasNext != true) break
            page++
        }
        out
    }

    suspend fun folders(): ChannelFoldersDto = withContext(Dispatchers.IO) {
        apiCall { api.folders() }
    }

    suspend fun details(id: Long): ChannelDto = withContext(Dispatchers.IO) {
        apiCall { api.details(id.toString()) }
    }

    /**
     * Asks the server to index what was posted since the last scan. Returns false
     * when a scan of that channel was already running: the caller waits for it all the same.
     */
    suspend fun startRefresh(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            apiCallNullable { api.refresh(id.toString(), ChannelRefreshRequest()) }
            true
        } catch (e: ApiException) {
            if (e.code == ALREADY_RUNNING) false else throw e
        }
    }

    suspend fun refreshRunning(id: Long): Boolean = withContext(Dispatchers.IO) {
        apiCall { api.refreshRunning(id.toString()) }
    }

    suspend fun refreshResult(id: Long): ChannelRefreshResultDto? = withContext(Dispatchers.IO) {
        apiCallNullable { api.refreshResult(id.toString()) }
    }

    private companion object {
        const val ALREADY_RUNNING = "already_running"
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 20
    }
}
