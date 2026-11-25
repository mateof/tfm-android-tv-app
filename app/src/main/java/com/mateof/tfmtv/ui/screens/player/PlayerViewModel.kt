package com.mateof.tfmtv.ui.screens.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.LibraryFileDto
import com.mateof.tfmtv.data.model.LibraryItemDetailDto
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.repo.LibraryRepository
import com.mateof.tfmtv.data.repo.MediaUrls
import com.mateof.tfmtv.data.repo.WatchRepository
import com.mateof.tfmtv.media.PlayLauncher
import com.mateof.tfmtv.media.VideoPlayers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** One audio or subtitle track. [id] is its place in the groups of the player. */
data class PlayerTrack(val id: String, val label: String, val language: String?, val selected: Boolean)

data class NextEpisode(
    val file: LibraryFileDto,
    val title: String,
    /** "T1 E4 · El aviso". */
    val label: String,
    val imageUrl: String?
)

data class PlayerState(
    val title: String = "",
    val subtitle: String? = null,
    val playing: Boolean = false,
    /** Meant to be playing, even while it buffers. */
    val wantsToPlay: Boolean = true,
    val buffering: Boolean = true,
    val ended: Boolean = false,
    val error: String? = null,
    val positionMs: Long = 0,
    val bufferedMs: Long = 0,
    val durationMs: Long = 0,
    val audio: List<PlayerTrack> = emptyList(),
    val text: List<PlayerTrack> = emptyList(),
    val textOff: Boolean = true,
    val speed: Float = 1f,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val previous: NextEpisode? = null,
    val next: NextEpisode? = null,
    val autoplay: Boolean = true,
    val notice: String? = null
)

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val prefs: ServerPreferences,
    private val watch: WatchRepository,
    private val library: LibraryRepository,
    private val launcher: PlayLauncher,
    private val players: VideoPlayers,
    private val mediaUrls: MediaUrls
) : ViewModel() {

    val player: ExoPlayer

    private val _state = MutableStateFlow(PlayerState(autoplay = prefs.autoplayNext.value))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var prepared = false
    private var url = ""
    private var channelId = 0L
    private var fileId = ""
    private var saver: Job? = null
    private var clock: Job? = null
    private var noticeJob: Job? = null
    private var warnedAboutSaving = false

    /**
     * From opening a video until it is ready. Until then the player still
     * answers with the position and the end of the previous one, which would
     * be saved as the progress of this one.
     */
    private var opening = false

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
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        applyPreferredLanguages()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (isPlaying) startSaving() else {
                    stopSaving()
                    if (!_state.value.ended) save()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _state.update { it.copy(wantsToPlay = playWhenReady) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                if (playbackState == Player.STATE_READY) {
                    opening = false
                    _state.update { it.copy(error = null, ended = false) }
                }
                if (playbackState == Player.STATE_ENDED && !opening && !_state.value.ended) {
                    _state.update { it.copy(ended = true) }
                    save(completed = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(error = describe(error), buffering = false) }
            }

            override fun onTracksChanged(tracks: Tracks) = readTracks(tracks)
        })
        clock = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        if (opening) return
        val duration = player.duration
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0),
                bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                durationMs = if (duration == C.TIME_UNSET || duration < 0) 0 else duration
            )
        }
    }

    fun prepare(url: String, title: String, channelId: Long, fileId: String, startMs: Long) {
        if (prepared || url.isBlank()) return
        prepared = true
        open(url, title, channelId, fileId, startMs)
    }

    private fun open(url: String, title: String, channelId: Long, fileId: String, startMs: Long) {
        opening = true
        this.url = url
        this.channelId = channelId
        this.fileId = fileId
        _state.update {
            it.copy(
                title = title, subtitle = null, ended = false, error = null, buffering = true,
                positionMs = startMs, bufferedMs = 0, durationMs = 0, previous = null, next = null
            )
        }
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        )
        player.prepare()
        if (startMs > 0) {
            player.seekTo(startMs)
            notice("Continuando desde ${Format.duration(startMs)}")
        }
        player.playWhenReady = true
        loadEpisodes()
    }

    // ----- what comes before and after in the series -----

    private fun loadEpisodes() {
        if (!tracksProgress) return
        val channel = channelId
        val id = fileId
        viewModelScope.launch {
            // Not a library file, or no library at all: there is simply no next one.
            val found = runCatching {
                val file = library.file(channel, id) ?: return@launch
                val item = file.itemId?.let { library.item(it) } ?: return@launch
                file to item
            }.getOrNull() ?: return@launch
            if (channel != channelId || id != fileId) return@launch
            val (file, item) = found
            if (!item.isSeries) {
                _state.update { it.copy(title = item.title, subtitle = item.year?.toString()) }
                return@launch
            }
            val episodes = item.seasons.sortedBy { it.number }.flatMap { s -> s.episodes.sortedBy { it.number } }
            val here = episodes.indexOfFirst { e -> e.files.any { it.channelId == channel && it.fileId == id } }
            if (here < 0) return@launch
            val current = episodes[here]
            // Specials are season 0: they are never the continuation of anything, nor the other way round.
            fun neighbour(range: Iterable<Int>) = range.map { episodes[it] }
                .firstOrNull { it.files.isNotEmpty() && (it.season == 0) == (current.season == 0) }
            _state.update {
                it.copy(
                    title = item.title,
                    subtitle = listOfNotNull(
                        "T${current.season} E${current.number}",
                        current.title?.takeIf { t -> t.isNotBlank() }
                    ).joinToString(" · "),
                    next = neighbour(here + 1 until episodes.size)?.let { e -> episodeOf(item, e.season, e.number, e.title, e.stillUrl, e.files, file) },
                    previous = neighbour((0 until here).reversed())?.let { e -> episodeOf(item, e.season, e.number, e.title, e.stillUrl, e.files, file) }
                )
            }
        }
    }

    private fun episodeOf(
        item: LibraryItemDetailDto,
        season: Int,
        number: Int,
        title: String?,
        still: String?,
        files: List<LibraryFileDto>,
        current: LibraryFileDto
    ) = NextEpisode(
        // With several copies, the one from the same channel as what is playing.
        file = files.firstOrNull { it.channelId == current.channelId } ?: files.first(),
        title = "${item.title} · T$season E$number",
        label = "T$season E$number" + (title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        imageUrl = mediaUrls.libraryImage(still ?: item.backdropUrl, "w300")
    )

    fun playNext() = _state.value.next?.let(::switchTo)
    fun playPrevious() = _state.value.previous?.let(::switchTo)

    private fun switchTo(episode: NextEpisode) {
        // What is left keeps its place, unless it was seen to the end.
        if (!_state.value.ended) save(completed = if (nearEnd()) true else null)
        val file = episode.file
        val target = launcher.internalUrl(file.file)
        if (target == null) {
            notice("Este episodio no tiene URL de reproducción")
            return
        }
        player.stop()
        open(target, episode.title, file.channelId, file.fileId, file.watch?.takeIf { it.inProgress }?.positionMs ?: 0L)
    }

    private fun nearEnd(): Boolean {
        val s = _state.value
        return s.durationMs > 0 && s.durationMs - s.positionMs < NEXT_PROMPT_MS
    }

    // ----- controls -----

    fun togglePlay() {
        if (_state.value.ended) {
            player.seekTo(0)
            player.playWhenReady = true
        } else player.playWhenReady = !player.playWhenReady
    }

    fun setPlaying(play: Boolean) {
        player.playWhenReady = play
    }

    fun seekTo(positionMs: Long) {
        val end = _state.value.durationMs
        val target = if (end > 0) positionMs.coerceIn(0, end) else positionMs.coerceAtLeast(0)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target, ended = false) }
    }

    fun seekBy(ms: Long) = seekTo(_state.value.positionMs + ms)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setResizeMode(mode: Int) = _state.update { it.copy(resizeMode = mode) }

    fun retry() {
        val at = _state.value.positionMs
        _state.update { it.copy(error = null, buffering = true) }
        player.prepare()
        if (at > 0) player.seekTo(at)
        player.playWhenReady = true
    }

    /** @return false when there is no app to hand it to. */
    fun openElsewhere(): Boolean {
        player.pause()
        return players.choose(url, _state.value.title)
    }

    // ----- tracks -----

    private fun applyPreferredLanguages() {
        val audio = prefs.audioLanguage.value
        val text = prefs.textLanguage.value
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                if (audio != null) setPreferredAudioLanguage(audio)
                when (text) {
                    null -> Unit
                    ServerPreferences.NO_SUBTITLES -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    else -> setPreferredTextLanguage(text)
                }
            }
            .build()
    }

    private fun readTracks(tracks: Tracks) {
        fun of(type: Int, name: String) = buildList {
            tracks.groups.forEachIndexed { g, group ->
                if (group.type != type) return@forEachIndexed
                for (t in 0 until group.length) {
                    if (!group.isTrackSupported(t)) continue
                    val format = group.getTrackFormat(t)
                    val language = format.language?.takeIf { it != C.LANGUAGE_UNDETERMINED }
                    val label = listOfNotNull(
                        language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale("es")).replaceFirstChar(Char::uppercase) },
                        format.label?.takeIf { it.isNotBlank() },
                        if (type == C.TRACK_TYPE_AUDIO) channelsLabel(format.channelCount) else null
                    ).joinToString(" · ").ifBlank { "$name ${size + 1}" }
                    add(PlayerTrack("$g:$t", label, language, group.isTrackSelected(t)))
                }
            }
        }
        val text = of(C.TRACK_TYPE_TEXT, "Subtítulos")
        _state.update {
            it.copy(
                audio = of(C.TRACK_TYPE_AUDIO, "Pista"),
                text = text,
                textOff = text.none { t -> t.selected }
            )
        }
    }

    private fun channelsLabel(count: Int): String? = when (count) {
        1 -> "Mono"
        2 -> "Estéreo"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }

    fun selectAudio(track: PlayerTrack) {
        select(C.TRACK_TYPE_AUDIO, track)
        track.language?.let { l -> viewModelScope.launch { prefs.saveAudioLanguage(l) } }
    }

    /** Null turns them off. The choice is kept for the next videos. */
    fun selectText(track: PlayerTrack?) {
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else select(C.TRACK_TYPE_TEXT, track)
        val value = if (track == null) ServerPreferences.NO_SUBTITLES else track.language ?: return
        viewModelScope.launch { prefs.saveTextLanguage(value) }
    }

    private fun select(type: Int, track: PlayerTrack) {
        val (g, t) = track.id.split(':').map { it.toInt() }
        val group = player.currentTracks.groups.getOrNull(g) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, t))
            .build()
    }

    // ----- progress on the server -----

    private fun startSaving() {
        if (saver?.isActive == true) return
        saver = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                save()
            }
        }
    }

    private fun stopSaving() {
        saver?.cancel()
        saver = null
    }

    /**
     * Reads the position on the main thread (ExoPlayer requirement) and ships
     * it. A duration of 0 means "not known yet": the server keeps the one it
     * already had, and the file still shows up in "continue watching", which
     * an early return would have silently prevented. Position 0 is not worth
     * reporting and would wipe a good one when the player is being released.
     */
    private fun snapshot(): Pair<Long, Long>? {
        if (!tracksProgress || opening) return null
        val position = player.currentPosition
        if (position <= 0) return null
        val duration = player.duration
        return position to (if (duration == C.TIME_UNSET || duration <= 0) 0L else duration)
    }

    private fun save(completed: Boolean? = null) {
        val (position, duration) = snapshot() ?: return
        // The next episode may be open by the time this runs.
        val channel = channelId
        val id = fileId
        viewModelScope.launch {
            val saved = watch.save(channel, id, position, duration, completed)
            // Say it once: a silent failure here is why progress can go missing
            if (!saved && !warnedAboutSaving) {
                warnedAboutSaving = true
                notice("No se pudo guardar el progreso en el servidor")
            }
        }
    }

    private fun notice(text: String) {
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            _state.update { it.copy(notice = text) }
            delay(NOTICE_MS)
            _state.update { it.copy(notice = null) }
        }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Se ha perdido la conexión con el servidor."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "El servidor no ha podido dar el vídeo. Puede que ya no esté en Telegram."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "El vídeo ya no está en el servidor."
        in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "El fichero está dañado o su formato no se puede leer."
        in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Este aparato no puede decodificar el vídeo. Prueba con otro reproductor."
        else -> "No se ha podido reproducir el vídeo (${error.errorCodeName})."
    }

    override fun onCleared() {
        clock?.cancel()
        stopSaving()
        val last = snapshot()
        val completed = if (_state.value.ended) true else null
        val channel = channelId
        val id = fileId
        player.release()
        if (last != null) {
            // viewModelScope is already cancelled here; the final save has its own scope.
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                withContext(NonCancellable) { watch.save(channel, id, last.first, last.second, completed) }
            }
        }
        super.onCleared()
    }

    companion object {
        /** The next episode is offered this long before the end. */
        const val NEXT_PROMPT_MS = 25_000L
        private const val SAVE_INTERVAL_MS = 10_000L
        private const val TICK_MS = 500L
        private const val NOTICE_MS = 3_500L
    }
}
