package com.mateof.tfmtv.data.repo

import android.util.Log
import com.mateof.tfmtv.core.apiCallNullable
import com.mateof.tfmtv.data.api.LibraryApi
import com.mateof.tfmtv.data.model.WatchStateDto
import com.mateof.tfmtv.data.model.WatchUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playback progress kept on the server. Every call swallows its errors:
 * playing a video must never depend on the progress being saved.
 */
@Singleton
class WatchRepository @Inject constructor(
    private val api: LibraryApi
) {
    suspend fun get(channelId: Long, fileId: String): WatchStateDto? = withContext(Dispatchers.IO) {
        runCatching { apiCallNullable { api.watch(channelId, fileId) } }
            .onFailure { Log.w(TAG, "Could not read progress of $channelId/$fileId: ${it.message}") }
            .getOrNull()
    }

    /** Position to start from, or 0 when the file was finished or barely started. */
    suspend fun resumePosition(channelId: Long, fileId: String): Long {
        val state = get(channelId, fileId) ?: return 0
        return if (!state.completed && state.positionMs > RESUME_MIN_MS) state.positionMs else 0
    }

    suspend fun save(channelId: Long, fileId: String, positionMs: Long, durationMs: Long, completed: Boolean? = null) {
        withContext(Dispatchers.IO) {
            runCatching {
                apiCallNullable {
                    api.updateWatch(channelId, fileId, WatchUpdateRequest(positionMs, durationMs, completed))
                }
            }.onFailure { Log.w(TAG, "Could not save progress of $channelId/$fileId: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "WatchRepository"
        const val RESUME_MIN_MS = 30_000L
    }
}
