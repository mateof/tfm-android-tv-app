package com.mateof.tfmtv.media

import com.mateof.tfmtv.data.model.ApiFileDto
import com.mateof.tfmtv.data.repo.MediaUrls
import com.mateof.tfmtv.data.repo.WatchRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlayEvent {
    /** Play with the built-in player; [startMs] > 0 resumes. */
    data class Internal(
        val url: String,
        val title: String,
        val channelId: Long,
        val fileId: String,
        val startMs: Long
    ) : PlayEvent

    /** An external player took the URL. */
    data object Handed : PlayEvent

    data class Failed(val message: String) : PlayEvent
}

/**
 * Turns a file into a playback event: external player when configured,
 * otherwise the internal player with the server-side resume position.
 */
@Singleton
class PlayLauncher @Inject constructor(
    private val mediaUrls: MediaUrls,
    private val players: VideoPlayers,
    private val watch: WatchRepository
) {
    /**
     * @param startMs explicit start position; null asks the server for the
     * saved one (and resumes automatically when there is one worth resuming).
     */
    suspend fun resolve(file: ApiFileDto, channelId: Long, title: String, startMs: Long?): PlayEvent {
        val url = mediaUrls.withKey(file.streamUrl ?: file.downloadUrl)
            ?: return PlayEvent.Failed("Este vídeo no tiene URL de reproducción")
        if (players.launchExternal(url, title)) return PlayEvent.Handed
        val start = startMs ?: watch.resumePosition(channelId, file.id)
        return PlayEvent.Internal(url, title, channelId, file.id, start)
    }
}
