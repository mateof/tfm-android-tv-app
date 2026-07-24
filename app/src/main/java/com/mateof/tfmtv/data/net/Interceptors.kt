package com.mateof.tfmtv.data.net

import com.mateof.tfmtv.data.prefs.ServerPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit is built once with a placeholder base URL; this interceptor swaps
 * scheme/host/port for whatever server the user configured, so changing the
 * server never requires rebuilding the network stack.
 */
@Singleton
class HostSelectionInterceptor @Inject constructor(
    private val prefs: ServerPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = prefs.current.normalizedBaseUrl
        val request = chain.request()
        val target = base.toHttpUrlOrNull() ?: return chain.proceed(request)
        val newUrl = request.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

@Singleton
class ApiKeyInterceptor @Inject constructor(
    private val prefs: ServerPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = prefs.current.apiKey
        val request = if (key.isNotBlank()) {
            chain.request().newBuilder().header("X-Api-Key", key).build()
        } else chain.request()
        return chain.proceed(request)
    }
}
