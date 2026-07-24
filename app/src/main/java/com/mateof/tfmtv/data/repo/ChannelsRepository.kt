package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.apiCall
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
    suspend fun all(): List<ChannelDto> = withContext(Dispatchers.IO) {
        apiCall { api.list(sortBy = "name", pageSize = 200) }
    }

    suspend fun favorites(): List<ChannelDto> = withContext(Dispatchers.IO) {
        apiCall { api.list(favoritesOnly = true, sortBy = "name", pageSize = 200) }
    }

    suspend fun folders(): ChannelFoldersDto = withContext(Dispatchers.IO) {
        apiCall { api.folders() }
    }

    suspend fun details(id: Long): ChannelDto = withContext(Dispatchers.IO) {
        apiCall { api.details(id.toString()) }
    }
}
