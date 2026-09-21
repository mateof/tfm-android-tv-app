package com.mateof.tfmtv.ui.screens.player

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.WatchRepository
import com.mateof.tfmtv.ui.components.tapClick
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    prefs: ServerPreferences,
    private val watch: WatchRepository
) : ViewModel() {

    val player: ExoPlayer

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode = _resizeMode.asStateFlow()

    /** Short overlay shown when playback resumes from a saved position. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    private var prepared = false
    private var channelId = 0L
    private var fileId = ""
    private var ticker: Job? = null
    private var ended = false
    private var warnedAboutSaving = false

    private val tracksProgress get() = channelId != 0L && fileId.isNotBlank()

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
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startTicker() else {
                    stopTicker()
                    if (!ended) save()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !ended) {
                    ended = true
                    save(completed = true)
                }
            }
        })
    }

    fun prepare(url: String, title: String, channelId: Long, fileId: String, startMs: Long) {
        if (prepared || url.isBlank()) return
        prepared = true
        this.channelId = channelId
        this.fileId = fileId
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        )
        player.prepare()
        if (startMs > 0) {
            player.seekTo(startMs)
            _notice.value = "Continuando desde ${Format.duration(startMs)}"
            viewModelScope.launch {
                delay(3_000)
                _notice.value = null
            }
        }
        player.playWhenReady = true
    }

    fun cycleResizeMode() {
        _resizeMode.value = when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                save()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    /**
     * Reads the position on the main thread (ExoPlayer requirement) and ships
     * it. A duration of 0 means "not known yet": the server keeps the one it
     * already had, and the file still shows up in "continue watching", which
     * an early return would have silently prevented. Position 0 is not worth
     * reporting and would wipe a good one when the player is being released.
     */
    private fun snapshot(): Pair<Long, Long>? {
        if (!tracksProgress) return null
        val position = player.currentPosition
        if (position <= 0) return null
        val duration = player.duration
        return position to (if (duration == C.TIME_UNSET || duration <= 0) 0L else duration)
    }

    private fun save(completed: Boolean? = null) {
        val (position, duration) = snapshot() ?: return
        viewModelScope.launch {
            val saved = watch.save(channelId, fileId, position, duration, completed)
            // Say it once: a silent failure here is why progress can go missing
            if (!saved && !warnedAboutSaving) {
                warnedAboutSaving = true
                _notice.value = "No se pudo guardar el progreso en el servidor"
                delay(5_000)
                _notice.value = null
            }
        }
    }

    override fun onCleared() {
        stopTicker()
        val last = snapshot()
        val completed = if (ended) true else null
        player.release()
        if (last != null) {
            // viewModelScope is already cancelled here; the final save has its own scope.
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                withContext(NonCancellable) { watch.save(channelId, fileId, last.first, last.second, completed) }
            }
        }
        super.onCleared()
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 10_000L
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    channelId: Long = 0,
    fileId: String = "",
    startMs: Long = 0,
    onBack: () -> Unit
) {
    val vm: PlayerViewModel = hiltViewModel()
    val resizeMode by vm.resizeMode.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()

    LaunchedEffect(url) { vm.prepare(url, title, channelId, fileId, startMs) }
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
        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}
