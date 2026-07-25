package com.mateof.tfmtv.data.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// System / auth
// ---------------------------------------------------------------------------

@Serializable
data class SystemInfoDto(
    val product: String? = null,
    val version: String? = null,
    val apiVersion: String? = null,
    val mongoConnected: Boolean = false,
    val telegramConfigured: Boolean = false,
    val telegramAuthenticated: Boolean = false,
    val setupComplete: Boolean = false,
    val requiresApiKey: Boolean = false
)

@Serializable
data class TelegramUserDto(
    val id: Long = 0,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val isPremium: Boolean = false
)

@Serializable
data class AuthStatusDto(
    val step: String? = null,
    val isAuthenticated: Boolean = false,
    val isConfigured: Boolean = false,
    val user: TelegramUserDto? = null
)

// ---------------------------------------------------------------------------
// Channels
// ---------------------------------------------------------------------------

@Serializable
data class ChannelDto(
    val id: Long = 0,
    val name: String? = null,
    val type: String? = null,
    val isOwner: Boolean = false,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val imageUrl: String? = null,
    val hasDatabase: Boolean = false,
    val fileCount: Long? = null,
    val folderCount: Long? = null,
    val totalSize: Long? = null,
    val totalSizeText: String? = null,
    val videoCount: Long? = null
)

@Serializable
data class ChatFolderDto(
    val id: Long = 0,
    val title: String? = null,
    val iconEmoji: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    val channelCount: Int = 0
)

@Serializable
data class ChannelFoldersDto(
    val folders: List<ChatFolderDto> = emptyList(),
    val ungrouped: List<ChannelDto> = emptyList(),
    val totalChannels: Int = 0
)

@Serializable
data class ChannelMessageDto(
    val id: Long = 0,
    val date: String? = null,
    val text: String? = null,
    val hasMedia: Boolean = false,
    val mediaType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val from: String? = null
)

// ---------------------------------------------------------------------------
// Files
// ---------------------------------------------------------------------------

@Serializable
data class ApiFileDto(
    val id: String = "",
    val name: String = "",
    val path: String? = null,
    val parentId: String? = null,
    val isFile: Boolean = false,
    val hasChildren: Boolean = false,
    val size: Long? = null,
    val sizeText: String? = null,
    val type: String? = null,
    val category: String? = null,
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val messageId: Long? = null,
    val isSplit: Boolean = false,
    val streamUrl: String? = null,
    val downloadUrl: String? = null
)

@Serializable
data class FolderStatsDto(
    val folderCount: Long = 0,
    val fileCount: Long = 0,
    val videoCount: Long = 0,
    val totalSize: Long = 0,
    val totalSizeText: String? = null
)

@Serializable
data class BreadcrumbDto(
    val name: String = "",
    val path: String = "/",
    val folderId: String? = null
)

@Serializable
data class FolderContentsDto(
    val channelId: String? = null,
    val currentPath: String? = null,
    val currentFolderId: String? = null,
    val parentFolderId: String? = null,
    val parentPath: String? = null,
    val folderName: String? = null,
    val items: List<ApiFileDto> = emptyList(),
    val stats: FolderStatsDto? = null,
    val breadcrumbs: List<BreadcrumbDto> = emptyList()
)
