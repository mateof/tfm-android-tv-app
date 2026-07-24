package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.data.api.ChannelsApi
import com.mateof.tfmtv.data.api.FilesApi
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.model.FolderContentsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val files: FilesApi,
    private val channels: ChannelsApi
) {
    /** One folder level: subfolders plus the videos directly inside it. */
    suspend fun browse(
        channelId: Long,
        path: String? = null,
        folderId: String? = null,
        page: Int = 1
    ): FolderContentsDto = withContext(Dispatchers.IO) {
        apiCall {
            files.browse(
                channelId = channelId.toString(),
                path = path,
                folderId = folderId,
                filter = "video",
                sortBy = "date",
                sortDescending = true,
                page = page,
                pageSize = PAGE_SIZE
            )
        }
    }

    /**
     * Every video in the channel, newest first. The server search is recursive
     * but needs a non-empty term, and it matches with `contains`, so a dot hits
     * anything carrying a file extension.
     */
    suspend fun allVideos(channelId: Long, page: Int = 1): List<ApiFileDto> =
        withContext(Dispatchers.IO) {
            apiCall {
                files.search(
                    channelId = channelId.toString(),
                    q = ".",
                    filter = "video",
                    sortBy = "date",
                    sortDescending = true,
                    page = page,
                    pageSize = PAGE_SIZE
                )
            }
        }

    /**
     * Video files keyed by the message they came from, so the messages list can
     * play without a lookup per card.
     */
    suspend fun videoIndex(channelId: Long): Map<Long, ApiFileDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                apiCall {
                    files.search(
                        channelId = channelId.toString(),
                        q = ".",
                        filter = "video",
                        sortBy = "date",
                        sortDescending = true,
                        pageSize = INDEX_SIZE
                    )
                }
            }.getOrDefault(emptyList())
                .mapNotNull { file -> file.messageId?.let { it to file } }
                .toMap()
        }

    /** Channel messages that carry a video, newest first. */
    suspend fun videoMessages(channelId: Long, offset: Int = 0): List<ChannelMessageDto> =
        withContext(Dispatchers.IO) {
            apiCall {
                channels.messages(
                    id = channelId.toString(),
                    limit = MESSAGE_PAGE,
                    offset = offset,
                    onlyMedia = true
                )
            }.filter { it.mediaType.equals("video", ignoreCase = true) }
        }

    /**
     * Finds the indexed file backing a message. Streaming needs a file id, and
     * messages only carry a name, so the name is searched and confirmed against
     * the message id when the channel has been indexed more than once.
     */
    suspend fun resolveMessage(channelId: Long, message: ChannelMessageDto): ApiFileDto? =
        withContext(Dispatchers.IO) {
            val name = message.fileName?.takeIf { it.isNotBlank() } ?: return@withContext null
            val matches = runCatching {
                apiCall {
                    files.search(
                        channelId = channelId.toString(),
                        q = name,
                        filter = "video",
                        pageSize = 50
                    )
                }
            }.getOrDefault(emptyList())

            matches.firstOrNull { it.messageId == message.id }
                ?: matches.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

    private companion object {
        const val PAGE_SIZE = 200
        const val INDEX_SIZE = 500
        const val MESSAGE_PAGE = 100
    }
}
