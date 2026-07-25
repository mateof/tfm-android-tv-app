package com.mateof.tfmtv.data.api

import com.mateof.tfmtv.core.ApiEnvelope
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.AuthStatusDto
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChannelFoldersDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.model.FolderContentsDto
import com.mateof.tfmtv.data.model.SystemInfoDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SystemApi {
    @GET("api/v1/system/ping")
    suspend fun ping(): ApiEnvelope<String>

    @GET("api/v1/system/info")
    suspend fun info(): ApiEnvelope<SystemInfoDto>
}

interface AuthApi {
    @GET("api/v1/auth/status")
    suspend fun status(): ApiEnvelope<AuthStatusDto>
}

interface ChannelsApi {

    @GET("api/v1/channels")
    suspend fun list(
        @Query("onlySaved") onlySaved: Boolean = false,
        @Query("favoritesOnly") favoritesOnly: Boolean = false,
        @Query("includeHidden") includeHidden: Boolean = false,
        @Query("search") search: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<List<ChannelDto>>

    @GET("api/v1/channels/folders")
    suspend fun folders(): ApiEnvelope<ChannelFoldersDto>

    @GET("api/v1/channels/{id}")
    suspend fun details(@Path("id") id: String): ApiEnvelope<ChannelDto>

    @GET("api/v1/channels/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("onlyMedia") onlyMedia: Boolean = true
    ): ApiEnvelope<List<ChannelMessageDto>>
}

interface FilesApi {

    @GET("api/v1/channels/{channelId}/files")
    suspend fun browse(
        @Path("channelId") channelId: String,
        @Query("path") path: String? = null,
        @Query("folderId") folderId: String? = null,
        @Query("filter") filter: String? = null,
        @Query("filesOnly") filesOnly: Boolean = false,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<FolderContentsDto>

    /** Recursive over the subtree rooted at [path]; `q` is matched with `contains`. */
    @GET("api/v1/channels/{channelId}/files/search")
    suspend fun search(
        @Path("channelId") channelId: String,
        @Query("q") q: String,
        @Query("path") path: String? = null,
        @Query("filter") filter: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<List<ApiFileDto>>
}
