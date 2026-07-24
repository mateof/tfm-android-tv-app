package com.mateof.tfmtv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
}

/** URL building is needed inside cards, where a ViewModel would be overkill. */
@Composable
fun rememberMediaUrls(): MediaUrls {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, MediaUrlsEntryPoint::class.java).mediaUrls()
    }
}
