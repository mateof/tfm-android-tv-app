package com.mateof.tfmtv.data.api

import com.mateof.tfmtv.core.ApiEnvelope
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.AuthStatusDto
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChannelFoldersDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.model.FolderContentsDto
import com.mateof.tfmtv.data.model.SystemInfoDto
import com.mateof.tfmtv.data.model.IdentifyItemRequest
import com.mateof.tfmtv.data.model.ItemWatchedRequest
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDetailDto
import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.LibraryStatsDto
import com.mateof.tfmtv.data.model.MatchFileRequest
import com.mateof.tfmtv.data.model.ProviderCandidateDto
import com.mateof.tfmtv.data.model.WatchStateDto
import com.mateof.tfmtv.data.model.WatchUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
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

interface LibraryApi {

    @GET("api/v1/library/stats")
    suspend fun stats(): ApiEnvelope<LibraryStatsDto>

    @GET("api/v1/library/items")
    suspend fun items(
        @Query("kind") kind: String? = null,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("sortBy") sortBy: String? = "title",
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 500
    ): ApiEnvelope<List<LibraryItemDto>>

    @GET("api/v1/library/items/{id}")
    suspend fun item(@Path("id") id: String): ApiEnvelope<LibraryItemDetailDto>

    @GET("api/v1/library/continue")
    suspend fun continueWatching(@Query("limit") limit: Int = 100): ApiEnvelope<List<LibraryFileDto>>

    @GET("api/v1/library/files")
    suspend fun files(
        @Query("status") status: String? = null,
        @Query("channelId") channelId: Long? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 500
    ): ApiEnvelope<List<LibraryFileDto>>

    @GET("api/v1/library/providers/search")
    suspend fun searchProviders(
        @Query("q") q: String? = null,
        @Query("kind") kind: String? = null,
        @Query("year") year: Int? = null,
        @Query("imdbId") imdbId: String? = null
    ): ApiEnvelope<List<ProviderCandidateDto>>

    @PUT("api/v1/library/files/{channelId}/{fileId}/match")
    suspend fun matchFile(
        @Path("channelId") channelId: Long,
        @Path("fileId") fileId: String,
        @Body body: MatchFileRequest
    ): ApiEnvelope<LibraryFileDto>

    @POST("api/v1/library/files/{channelId}/{fileId}/ignore")
    suspend fun ignoreFile(
        @Path("channelId") channelId: Long,
        @Path("fileId") fileId: String
    ): ApiEnvelope<LibraryFileDto>

    @PUT("api/v1/library/items/{id}/identify")
    suspend fun identifyItem(@Path("id") id: String, @Body body: IdentifyItemRequest): ApiEnvelope<LibraryItemDetailDto>

    @POST("api/v1/library/items/{id}/watched")
    suspend fun markItemWatched(@Path("id") id: String, @Body body: ItemWatchedRequest): ApiEnvelope<LibraryItemDetailDto>

    @DELETE("api/v1/library/items/{id}/watched")
    suspend fun markItemUnwatched(@Path("id") id: String): ApiEnvelope<LibraryItemDetailDto>

    @GET("api/v1/library/watch/{channelId}/{fileId}")
    suspend fun watch(@Path("channelId") channelId: Long, @Path("fileId") fileId: String): ApiEnvelope<WatchStateDto>

    @PUT("api/v1/library/watch/{channelId}/{fileId}")
    suspend fun updateWatch(
        @Path("channelId") channelId: Long,
        @Path("fileId") fileId: String,
        @Body body: WatchUpdateRequest
    ): ApiEnvelope<WatchStateDto>
}
