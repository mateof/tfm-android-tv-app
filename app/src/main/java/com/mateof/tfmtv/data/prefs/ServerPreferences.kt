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

    suspend fun awaitLoaded(): ServerConfig = configFlow.first()
}
