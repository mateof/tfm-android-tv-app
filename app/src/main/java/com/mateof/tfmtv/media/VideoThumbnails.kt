package com.mateof.tfmtv.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coil model for a frame extracted from a remote video. [id] identifies the file
 * itself so the cached frame survives an API key or server host change.
 */
data class VideoFrame(val url: String, val id: String)

/**
 * The server exposes no thumbnail endpoint, so frames are pulled from the
 * stream with ranged reads and kept as JPEGs on disk. Extraction is capped
 * because a TV stick chokes on more than a couple of concurrent decodes.
 */
@Singleton
class VideoThumbnails @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val gate = Semaphore(2)

    suspend fun frame(model: VideoFrame): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(dir, fileName(model.id))
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { return@withContext it }
            file.delete()
        }
        val bitmap = gate.withPermit { extract(model.url) } ?: return@withContext null
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
        bitmap
    }

    private fun extract(url: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, emptyMap())
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // A tenth in avoids black intros without seeking far into the file.
            val atUs = (durationMs / 10).coerceIn(0L, 60_000L) * 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    atUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    WIDTH,
                    HEIGHT
                )
            } else {
                retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun fileName(id: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val WIDTH = 480
        const val HEIGHT = 270
    }
}

class VideoFrameFetcher(
    private val model: VideoFrame,
    private val options: Options,
    private val thumbnails: VideoThumbnails
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = thumbnails.frame(model) ?: return null
        return DrawableResult(
            drawable = android.graphics.drawable.BitmapDrawable(
                options.context.resources,
                bitmap
            ),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val thumbnails: VideoThumbnails) : Fetcher.Factory<VideoFrame> {
        override fun create(data: VideoFrame, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoFrameFetcher(data, options, thumbnails)
    }
}

class VideoFrameKeyer : Keyer<VideoFrame> {
    override fun key(data: VideoFrame, options: Options): String = "frame:${data.id}"
}
