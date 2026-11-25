package com.mateof.tfmtv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateof.tfmtv.data.prefs.ServerPreferences
import androidx.compose.ui.platform.LocalContext
import com.mateof.tfmtv.data.repo.MediaUrls
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaUrlsEntryPoint {
    fun mediaUrls(): MediaUrls
    fun preferences(): ServerPreferences
}

@Composable
fun rememberPreferences(): ServerPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, MediaUrlsEntryPoint::class.java).preferences()
    }
}

@Composable
fun rememberThumbnailsEnabled(): Boolean {
    val enabled by rememberPreferences().thumbnails.collectAsStateWithLifecycle()
    return enabled
}

/** URL building is needed inside cards, where a ViewModel would be overkill. */
@Composable
fun rememberMediaUrls(): MediaUrls {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, MediaUrlsEntryPoint::class.java).mediaUrls()
    }
}
