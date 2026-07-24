package com.mateof.tfmtv.ui.screens.player

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.ui.components.tapClick
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    prefs: ServerPreferences
) : ViewModel() {

    val player: ExoPlayer

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode = _resizeMode.asStateFlow()

    private var prepared = false

    init {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply {
                val key = prefs.current.apiKey
                if (key.isNotBlank()) setDefaultRequestProperties(mapOf("X-Api-Key" to key))
            }
        // NextRenderersFactory adds FFmpeg software decoders. EXTENSION_RENDERER_MODE_ON
        // keeps hardware decoders first and falls back to FFmpeg for codecs the
        // stick cannot handle (Xvid/DivX, AC3/EAC3/DTS…), so MKV/AVI still play.
        val renderersFactory = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(context, httpFactory),
                    extractorsFactory
                )
            )
            .build()
    }

    fun prepare(url: String, title: String) {
        if (prepared || url.isBlank()) return
        prepared = true
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        )
        player.prepare()
        player.playWhenReady = true
    }

    fun cycleResizeMode() {
        _resizeMode.value = when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val vm: PlayerViewModel = hiltViewModel()
    val resizeMode by vm.resizeMode.collectAsStateWithLifecycle()

    LaunchedEffect(url) { vm.prepare(url, title) }
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = vm.player
                    keepScreenOn = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    controllerShowTimeoutMs = 4000
                    subtitleView?.setApplyEmbeddedStyles(true)
                    requestFocus()
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )
        Button(
            onClick = vm::cycleResizeMode,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).tapClick(vm::cycleResizeMode)
        ) {
            Text(
                when (resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Rellenar"
                    else -> "Ajustar"
                }
            )
        }
    }
}
