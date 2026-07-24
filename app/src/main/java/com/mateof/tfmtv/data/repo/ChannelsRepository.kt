package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.core.apiCallPaged
import com.mateof.tfmtv.data.api.ChannelsApi
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChannelFoldersDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelsRepository @Inject constructor(
    private val api: ChannelsApi
) {
    /**
     * Every visible chat, page by page. The sections and the search box filter
     * this list on the client, so a partial list silently hides channels.
     */
    suspend fun all(): List<ChannelDto> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ChannelDto>()
        var page = 1
        while (page <= MAX_PAGES) {
            val paged = apiCallPaged {
                api.list(sortBy = "name", page = page, pageSize = PAGE_SIZE)
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

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 20
    }
}
