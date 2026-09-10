package com.mateof.tfmtv.data.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Media library (/api/v1/library)
// ---------------------------------------------------------------------------

object LibraryKind {
    const val MOVIE = "movie"
    const val SERIES = "series"
}

object LibraryFileStatus {
    const val MATCHED = "matched"
    const val REVIEW = "review"
    const val UNMATCHED = "unmatched"
    const val IGNORED = "ignored"
}

@Serializable
data class WatchStateDto(
    val channelId: Long = 0,
    val fileId: String = "",
    val fileName: String? = null,
    val itemId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Double = 0.0,
    val completed: Boolean = false,
    val playCount: Int = 0,
    val firstPlayedAt: String? = null,
    val lastPlayedAt: String? = null
) {
    val inProgress: Boolean get() = !completed && positionMs > 0
}

@Serializable
data class WatchSummaryDto(
    val completed: Boolean = false,
    val inProgress: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Double = 0.0,
    val episodesTotal: Int = 0,
    val episodesWatched: Int = 0,
    val lastPlayedAt: String? = null
)

@Serializable
data class ParsedNameDto(
    val title: String = "",
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isSeries: Boolean = false,
    val source: String? = null
)

@Serializable
data class LibraryItemDto(
    val id: String = "",
    val kind: String = LibraryKind.MOVIE,
    val title: String = "",
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val voteCount: Int? = null,
    val runtime: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val provider: String? = null,
    val providerId: String? = null,
    val imdbId: String? = null,
    val locked: Boolean = false,
    val fileCount: Int = 0,
    val channelIds: List<Long> = emptyList(),
    val seasonCount: Int = 0,
    val addedAt: String? = null,
    val watch: WatchSummaryDto = WatchSummaryDto()
)

@Serializable
data class LibraryFileDto(
    val channelId: Long = 0,
    val fileId: String = "",
    val fileName: String = "",
    val folderPath: String? = null,
    val size: Long = 0,
    val sizeText: String? = null,
    val status: String = LibraryFileStatus.UNMATCHED,
    val confidence: Double = 0.0,
    val matchSource: String? = null,
    val locked: Boolean = false,
    val parsed: ParsedNameDto = ParsedNameDto(),
    val itemId: String? = null,
    val kind: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val file: ApiFileDto = ApiFileDto(),
    val watch: WatchStateDto? = null,
    val item: LibraryItemDto? = null,
    val scannedAt: String? = null,
    val error: String? = null
)

@Serializable
data class LibraryEpisodeDto(
    val season: Int = 0,
    val number: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val stillUrl: String? = null,
    val airDate: String? = null,
    val runtime: Int? = null,
    val rating: Double? = null,
    val files: List<LibraryFileDto> = emptyList(),
    val watch: WatchStateDto? = null,
    val completed: Boolean = false
)

@Serializable
data class LibrarySeasonDto(
    val number: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val episodeCount: Int? = null,
    val episodes: List<LibraryEpisodeDto> = emptyList(),
    val episodesWatched: Int = 0
)

@Serializable
data class LibraryItemDetailDto(
    val id: String = "",
    val kind: String = LibraryKind.MOVIE,
    val title: String = "",
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val voteCount: Int? = null,
    val runtime: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val provider: String? = null,
    val providerId: String? = null,
    val imdbId: String? = null,
    val locked: Boolean = false,
    val fileCount: Int = 0,
    val channelIds: List<Long> = emptyList(),
    val seasonCount: Int = 0,
    val addedAt: String? = null,
    val watch: WatchSummaryDto = WatchSummaryDto(),
    val tagline: String? = null,
    val status: String? = null,
    val files: List<LibraryFileDto> = emptyList(),
    val seasons: List<LibrarySeasonDto> = emptyList(),
    val nextUp: LibraryEpisodeDto? = null,
    val resume: LibraryFileDto? = null
) {
    val isSeries: Boolean get() = kind == LibraryKind.SERIES
}

@Serializable
data class ProviderCandidateDto(
    val provider: String = "",
    val providerId: String = "",
    val kind: String = LibraryKind.MOVIE,
    val title: String = "",
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val imdbId: String? = null
)

@Serializable
data class LibraryProviderDto(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = false,
    val hasKey: Boolean = false,
    val priority: Int = 0
)

@Serializable
data class LibraryScanStateDto(
    val running: Boolean = false,
    val cancelled: Boolean = false,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val scope: String? = null,
    val channelsTotal: Int = 0,
    val channelsScanned: Int = 0,
    val currentChannel: String? = null,
    val filesSeen: Int = 0,
    val filesNew: Int = 0,
    val matched: Int = 0,
    val review: Int = 0,
    val unmatched: Int = 0,
    val failed: Int = 0,
    val error: String? = null
)

@Serializable
data class LibraryStatsDto(
    val enabled: Boolean = false,
    val language: String? = null,
    val autoScan: Boolean = false,
    val watchedThreshold: Double = 0.0,
    val providers: List<LibraryProviderDto> = emptyList(),
    val ready: Boolean = false,
    val movies: Int = 0,
    val series: Int = 0,
    val files: Map<String, Int> = emptyMap(),
    val inProgress: Int = 0,
    val scan: LibraryScanStateDto? = null
)

// ----- request bodies -----

@Serializable
data class WatchUpdateRequest(
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean? = null
)

@Serializable
data class MatchFileRequest(
    val provider: String,
    val providerId: String,
    val kind: String,
    val season: Int? = null,
    val episode: Int? = null
)

@Serializable
data class IdentifyItemRequest(
    val provider: String,
    val providerId: String,
    val kind: String? = null
)

@Serializable
data class ItemWatchedRequest(val season: Int? = null)
