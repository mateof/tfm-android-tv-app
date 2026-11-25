package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.Paged
import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.core.apiCallPaged
import com.mateof.tfmtv.data.api.ChannelsApi
import com.mateof.tfmtv.data.api.FilesApi
import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.model.ChannelMessageDto
import com.mateof.tfmtv.data.model.FolderContentsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A page of the messages with a video. [next] goes back into the next call; null at the end. */
data class MessagePage(
    val items: List<ChannelMessageDto>,
    val next: Long?,
    /** In the whole chat, as Telegram reports it; null on an old server. */
    val total: Int? = null
)

@Singleton
class VideoRepository @Inject constructor(
    private val files: FilesApi,
    private val channels: ChannelsApi,
    private val features: ServerFeatures
) {
    /** One folder level: subfolders plus the videos directly inside it. */
    suspend fun browse(
        channelId: Long,
        path: String? = null,
        folderId: String? = null,
        sortBy: String = "name",
        sortDescending: Boolean = false,
        page: Int = 1
    ): Paged<FolderContentsDto> = withContext(Dispatchers.IO) {
        apiCallPaged {
            files.browse(
                channelId = channelId.toString(),
                path = path,
                folderId = folderId,
                filter = "video",
                sortBy = sortBy,
                sortDescending = sortDescending,
                page = page,
                pageSize = PAGE_SIZE
            )
        }
    }

    /**
     * The videos of the channel, or of the folder [path] and what is below it.
     * The server search is recursive but needs a non-empty term, and it matches
     * with `contains`, so a dot hits anything carrying a file extension.
     */
    suspend fun videos(
        channelId: Long,
        text: String = "",
        path: String? = null,
        sortBy: String = "date",
        sortDescending: Boolean = true,
        page: Int = 1
    ): Paged<List<ApiFileDto>> = withContext(Dispatchers.IO) {
        apiCallPaged {
            files.search(
                channelId = channelId.toString(),
                q = text.trim().ifBlank { "." },
                path = path,
                filter = "video",
                sortBy = sortBy,
                sortDescending = sortDescending,
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

    /**
     * Messages that carry a video, newest first. Telegram does the search when
     * the server can ask for it; an old server is read page by page instead,
     * and the text is then looked for in each page.
     */
    suspend fun videoMessages(channelId: Long, text: String = "", from: Long = 0): MessagePage =
        withContext(Dispatchers.IO) {
            val query = text.trim()
            if (features.has(Feature.MESSAGES_BROWSE)) {
                val found = apiCall {
                    channels.searchMessages(
                        id = channelId.toString(),
                        kind = "videos",
                        q = query.ifBlank { null },
                        offsetId = from
                    )
                }
                MessagePage(found.items, found.nextOffsetId, found.total)
            } else {
                val page = apiCall {
                    channels.messages(
                        id = channelId.toString(),
                        limit = MESSAGE_PAGE,
                        offset = from.toInt(),
                        onlyMedia = true
                    )
                }
                MessagePage(
                    items = page.filter {
                        it.mediaType.equals("video", ignoreCase = true) &&
                            (query.isBlank() || it.fileName.orEmpty().contains(query, ignoreCase = true))
                    },
                    // `onlyMedia` drops messages after paging, so a short page is not the end.
                    next = if (page.isEmpty()) null else from + MESSAGE_PAGE
                )
            }
        }

    /**
     * Finds the indexed file backing a message. Messages only carry a name, so
     * the name is searched and confirmed against the message id when the
     * channel has been indexed more than once.
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
        const val PAGE_SIZE = 100
        const val INDEX_SIZE = 500
        const val MESSAGE_PAGE = 100
    }
}
