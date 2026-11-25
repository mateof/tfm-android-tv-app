package com.mateof.tfmtv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "server_prefs")

data class ServerConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val configured: Boolean = false
) {
    /** Base url guaranteed to end without trailing slash, e.g. `http://host:5257`. */
    val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')
}

/**
 * Which app plays video files. Any other value is the package name of an
 * installed player (VLC, MX Player, …).
 */
object VideoPlayerChoice {
    const val INTERNAL = "internal"
    const val ASK = "ask"
    const val SYSTEM = "system"
}

/** How a video reaches a television sent to by DLNA. */
enum class CastRouteMode(val label: String) {
    AUTO("Automático"),
    DIRECT("Directo desde el servidor"),
    RELAY("A través de este aparato")
}

/** Focus colour of the whole app. */
enum class AccentChoice(val label: String) {
    OCEAN("Océano"),
    AURORA("Aurora"),
    EMERALD("Esmeralda"),
    SUNSET("Atardecer"),
    RUBY("Rubí")
}

/** Glows, backdrops and transitions; AUTO turns them off on devices short of memory. */
enum class EffectsMode(val label: String) {
    AUTO("Automático"),
    FULL("Completos"),
    REDUCED("Reducidos")
}

@Singleton
class ServerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val CONFIGURED = booleanPreferencesKey("configured")
        val VIDEO_PLAYER = stringPreferencesKey("video_player")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_channels")
        val CAST_ROUTE = stringPreferencesKey("cast_route")
        val CAST_ADDRESSES = stringSetPreferencesKey("cast_addresses")
        val ACCENT = stringPreferencesKey("accent")
        val EFFECTS = stringPreferencesKey("effects")
        val THUMBNAILS = booleanPreferencesKey("video_thumbnails")
        val AUTOPLAY = booleanPreferencesKey("autoplay_next")
        val AUDIO_LANGUAGE = stringPreferencesKey("audio_language")
        val TEXT_LANGUAGE = stringPreferencesKey("text_language")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val configFlow: Flow<ServerConfig> = context.dataStore.data.map { p ->
        ServerConfig(
            baseUrl = p[Keys.BASE_URL] ?: "",
            apiKey = p[Keys.API_KEY] ?: "",
            configured = p[Keys.CONFIGURED] ?: false
        )
    }

    /**
     * Hot cached config so interceptors and URL builders can read it synchronously
     * on the request path without blocking.
     */
    val config: StateFlow<ServerConfig> =
        configFlow.stateIn(scope, SharingStarted.Eagerly, ServerConfig())

    val current: ServerConfig get() = config.value

    val videoPlayer: StateFlow<String> = context.dataStore.data
        .map { it[Keys.VIDEO_PLAYER] ?: VideoPlayerChoice.INTERNAL }
        .stateIn(scope, SharingStarted.Eagerly, VideoPlayerChoice.INTERNAL)

    /** Channels hidden on the server stay out of the lists unless this is on. */
    val showHiddenChannels: StateFlow<Boolean> = context.dataStore.data
        .map { it[Keys.SHOW_HIDDEN] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val castRoute: StateFlow<CastRouteMode> = context.dataStore.data
        .map { p -> CastRouteMode.entries.firstOrNull { it.name == p[Keys.CAST_ROUTE] } ?: CastRouteMode.AUTO }
        .stateIn(scope, SharingStarted.Eagerly, CastRouteMode.AUTO)

    val accent: StateFlow<AccentChoice> = context.dataStore.data
        .map { p -> AccentChoice.entries.firstOrNull { it.name == p[Keys.ACCENT] } ?: AccentChoice.OCEAN }
        .stateIn(scope, SharingStarted.Eagerly, AccentChoice.OCEAN)

    val effects: StateFlow<EffectsMode> = context.dataStore.data
        .map { p -> EffectsMode.entries.firstOrNull { it.name == p[Keys.EFFECTS] } ?: EffectsMode.AUTO }
        .stateIn(scope, SharingStarted.Eagerly, EffectsMode.AUTO)

    /** Previews on the video cards, from the ones Telegram keeps in each message. */
    val thumbnails: StateFlow<Boolean> = context.dataStore.data
        .map { it[Keys.THUMBNAILS] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val autoplayNext: StateFlow<Boolean> = context.dataStore.data
        .map { it[Keys.AUTOPLAY] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    /** Last audio and subtitle language picked in the player; [NO_SUBTITLES] when they were turned off. */
    val audioLanguage: StateFlow<String?> = context.dataStore.data
        .map { it[Keys.AUDIO_LANGUAGE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val textLanguage: StateFlow<String?> = context.dataStore.data
        .map { it[Keys.TEXT_LANGUAGE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val recentSearches: Flow<List<String>> = context.dataStore.data
        .map { p -> p[Keys.RECENT_SEARCHES].orEmpty().split('\n').filter { it.isNotBlank() } }

    suspend fun saveAccent(value: AccentChoice) {
        context.dataStore.edit { p -> p[Keys.ACCENT] = value.name }
    }

    suspend fun saveEffects(value: EffectsMode) {
        context.dataStore.edit { p -> p[Keys.EFFECTS] = value.name }
    }

    suspend fun saveThumbnails(value: Boolean) {
        context.dataStore.edit { p -> p[Keys.THUMBNAILS] = value }
    }

    suspend fun saveAutoplayNext(value: Boolean) {
        context.dataStore.edit { p -> p[Keys.AUTOPLAY] = value }
    }

    suspend fun saveAudioLanguage(value: String) {
        context.dataStore.edit { p -> p[Keys.AUDIO_LANGUAGE] = value }
    }

    suspend fun saveTextLanguage(value: String) {
        context.dataStore.edit { p -> p[Keys.TEXT_LANGUAGE] = value }
    }

    suspend fun rememberSearch(text: String) {
        val clean = text.trim().replace('\n', ' ')
        if (clean.isBlank()) return
        context.dataStore.edit { p ->
            val known = p[Keys.RECENT_SEARCHES].orEmpty().split('\n').filter { it.isNotBlank() }
            p[Keys.RECENT_SEARCHES] = (listOf(clean) + known.filterNot { it.equals(clean, ignoreCase = true) })
                .take(MAX_SEARCHES).joinToString("\n")
        }
    }

    suspend fun forgetSearches() {
        context.dataStore.edit { p -> p.remove(Keys.RECENT_SEARCHES) }
    }

    /** Televisions typed in by hand: the ones discovery never finds, so they are kept. */
    val castAddresses: Flow<Set<String>> = context.dataStore.data.map { it[Keys.CAST_ADDRESSES].orEmpty() }

    suspend fun saveCastRoute(value: CastRouteMode) {
        context.dataStore.edit { p -> p[Keys.CAST_ROUTE] = value.name }
    }

    suspend fun rememberCastAddress(address: String) {
        context.dataStore.edit { p -> p[Keys.CAST_ADDRESSES] = p[Keys.CAST_ADDRESSES].orEmpty() + address }
    }

    suspend fun save(baseUrl: String, apiKey: String) {
        context.dataStore.edit { p ->
            p[Keys.BASE_URL] = baseUrl.trimEnd('/')
            p[Keys.API_KEY] = apiKey
            p[Keys.CONFIGURED] = true
        }
    }

    suspend fun saveVideoPlayer(value: String) {
        context.dataStore.edit { p -> p[Keys.VIDEO_PLAYER] = value }
    }

    suspend fun saveShowHiddenChannels(value: Boolean) {
        context.dataStore.edit { p -> p[Keys.SHOW_HIDDEN] = value }
    }

    /** Order of the library's movie or series grid, by [LibraryKind][com.mateof.tfmtv.data.model.LibraryKind]. */
    suspend fun librarySort(kind: String): String? = context.dataStore.data.first()[librarySortKey(kind)]

    suspend fun saveLibrarySort(kind: String, sort: String) {
        context.dataStore.edit { p -> p[librarySortKey(kind)] = sort }
    }

    private fun librarySortKey(kind: String) = stringPreferencesKey("library_sort_$kind")

    suspend fun awaitLoaded(): ServerConfig = configFlow.first()

    companion object {
        const val NO_SUBTITLES = "off"
        private const val MAX_SEARCHES = 8
    }
}
