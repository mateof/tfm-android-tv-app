package com.mateof.tfmtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class TfmTvApp : Application(), ImageLoaderFactory {

    @Inject lateinit var okHttp: OkHttpClient

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { okHttp }
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
