package com.mateof.tfmtv.data.api

import com.mateof.tfmtv.core.ApiEnvelope
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.AuthStatusDto
import com.mateof.tfmtv.data.model.CapabilitiesDto
import com.mateof.tfmtv.data.model.ChannelDto
import com.mateof.tfmtv.data.model.ChannelRefreshProgressDto
import com.mateof.tfmtv.data.model.ChannelRefreshRequest
import com.mateof.tfmtv.data.model.ChannelRefreshResultDto
import com.mateof.tfmtv.data.model.ChannelFoldersDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.model.ChatSearchDto
import com.mateof.tfmtv.data.model.FolderContentsDto
import com.mateof.tfmtv.data.model.GlobalSearchDto
import com.mateof.tfmtv.data.model.SystemInfoDto
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
import com.mateof.tfmtv.data.model.WatchStateDto
import com.mateof.tfmtv.data.model.WatchUpdateRequest
import kotlinx.serialization.json.JsonElement
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

    /** Optional features of the server; a 404 is a server too old to have any. */
    @GET("api/mobile/capabilities")
    suspend fun capabilities(): ApiEnvelope<CapabilitiesDto>
}

interface SearchApi {
    /** Files by name in every indexed channel (`search.v1`). */
    @GET("api/v1/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("filter") filter: String = "video",
        @Query("scope") scope: String = "channels",
        @Query("limit") limit: Int = 100
    ): ApiEnvelope<GlobalSearchDto>
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

    /**
     * Searches the whole chat on Telegram, indexed or not (`messages.browse`).
     * Newest first; go on with `offsetId` until `nextOffsetId` is null.
     */
    @GET("api/v1/channels/{id}/messages/search")
    suspend fun searchMessages(
        @Path("id") id: String,
        @Query("kind") kind: String,
        @Query("q") q: String? = null,
        @Query("offsetId") offsetId: Long = 0,
        @Query("limit") limit: Int = 60
    ): ApiEnvelope<ChatSearchDto>

    @POST("api/v1/channels/{id}/favorite")
    suspend fun addFavorite(@Path("id") id: String): ApiEnvelope<JsonElement>

    @DELETE("api/v1/channels/{id}/favorite")
    suspend fun removeFavorite(@Path("id") id: String): ApiEnvelope<JsonElement>

    @POST("api/v1/channels/{id}/hidden")
    suspend fun hide(@Path("id") id: String): ApiEnvelope<JsonElement>

    @DELETE("api/v1/channels/{id}/hidden")
    suspend fun unhide(@Path("id") id: String): ApiEnvelope<JsonElement>

    /** Starts indexing the files posted since the last scan; answers 409 if one is running. */
    @POST("api/v1/channels/{id}/refresh")
    suspend fun refresh(@Path("id") id: String, @Body body: ChannelRefreshRequest): ApiEnvelope<JsonElement>

    @GET("api/v1/channels/{id}/refresh")
    suspend fun refreshRunning(@Path("id") id: String): ApiEnvelope<Boolean>

    /** `data: null` when nothing runs (`channels.refreshProgress`). */
    @GET("api/v1/channels/{id}/refresh/progress")
    suspend fun refreshProgress(@Path("id") id: String): ApiEnvelope<ChannelRefreshProgressDto>

    @DELETE("api/v1/channels/{id}/refresh")
    suspend fun cancelRefresh(@Path("id") id: String): ApiEnvelope<JsonElement>

    /** `data: null` until a refresh has finished since the server started. */
    @GET("api/v1/channels/{id}/refresh/result")
    suspend fun refreshResult(@Path("id") id: String): ApiEnvelope<ChannelRefreshResultDto>
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
        @Query("includeHidden") includeHidden: Boolean = false,
        @Query("sortBy") sortBy: String? = "title",
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 500
    ): ApiEnvelope<List<LibraryItemDto>>

    @POST("api/v1/library/scan")
    suspend fun scan(@Body body: LibraryScanRequest): ApiEnvelope<JsonElement>

    @GET("api/v1/library/scan")
    suspend fun scanState(): ApiEnvelope<LibraryScanStateDto>

    @DELETE("api/v1/library/scan")
    suspend fun cancelScan(): ApiEnvelope<JsonElement>

    @GET("api/v1/library/recent")
    suspend fun recent(
        @Query("kind") kind: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("includeHidden") includeHidden: Boolean = false
    ): ApiEnvelope<List<LibraryItemDto>>

    @GET("api/v1/library/watch/history")
    suspend fun history(
        @Query("limit") limit: Int = 100,
        @Query("includeHidden") includeHidden: Boolean = false
    ): ApiEnvelope<List<LibraryFileDto>>

    @GET("api/v1/library/files/{channelId}/{fileId}")
    suspend fun file(@Path("channelId") channelId: Long, @Path("fileId") fileId: String): ApiEnvelope<LibraryFileDto>

    /** Downloads the metadata and the episodes of an item again. */
    @POST("api/v1/library/items/{id}/refresh")
    suspend fun refreshItem(@Path("id") id: String): ApiEnvelope<JsonElement>

    @GET("api/v1/library/items/{id}")
    suspend fun item(@Path("id") id: String): ApiEnvelope<LibraryItemDetailDto>

    @GET("api/v1/library/continue")
    suspend fun continueWatching(
        @Query("limit") limit: Int = 100,
        @Query("includeHidden") includeHidden: Boolean = false
    ): ApiEnvelope<List<LibraryFileDto>>

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
    suspend fun markItemUnwatched(@Path("id") id: String, @Query("season") season: Int? = null): ApiEnvelope<LibraryItemDetailDto>

    @POST("api/v1/library/watch/{channelId}/{fileId}/watched")
    suspend fun markWatched(@Path("channelId") channelId: Long, @Path("fileId") fileId: String): ApiEnvelope<WatchStateDto>

    @DELETE("api/v1/library/watch/{channelId}/{fileId}/watched")
    suspend fun markUnwatched(@Path("channelId") channelId: Long, @Path("fileId") fileId: String): ApiEnvelope<WatchStateDto>

    /** Forgets the progress of a file: it leaves "continue watching". */
    @DELETE("api/v1/library/watch/{channelId}/{fileId}")
    suspend fun deleteWatch(@Path("channelId") channelId: Long, @Path("fileId") fileId: String): ApiEnvelope<JsonElement>

    @GET("api/v1/library/watch/{channelId}/{fileId}")
    suspend fun watch(@Path("channelId") channelId: Long, @Path("fileId") fileId: String): ApiEnvelope<WatchStateDto>

    @PUT("api/v1/library/watch/{channelId}/{fileId}")
    suspend fun updateWatch(
        @Path("channelId") channelId: Long,
        @Path("fileId") fileId: String,
        @Body body: WatchUpdateRequest
    ): ApiEnvelope<WatchStateDto>
}
