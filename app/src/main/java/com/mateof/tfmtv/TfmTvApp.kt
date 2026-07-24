package com.mateof.tfmtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mateof.tfmtv.media.VideoFrameFetcher
import com.mateof.tfmtv.media.VideoFrameKeyer
import com.mateof.tfmtv.media.VideoThumbnails
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class TfmTvApp : Application(), ImageLoaderFactory {

    @Inject lateinit var okHttp: OkHttpClient
    @Inject lateinit var thumbnails: VideoThumbnails

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { okHttp }
        .components {
            add(VideoFrameKeyer())
            add(VideoFrameFetcher.Factory(thumbnails))
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
