package com.mateof.tfmtv.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: ApiErrorBody? = null,
    val message: String? = null,
    val page: PageInfo? = null
)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val detail: String? = null
)

@Serializable
data class PageInfo(
    val page: Int = 1,
    val pageSize: Int = 50,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

class ApiException(
    val code: String,
    override val message: String,
    val detail: String? = null,
    val httpStatus: Int? = null
) : Exception(message)

data class Paged<T>(val items: T, val page: PageInfo?)

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Unwraps the standard `{success, data, error, message, page}` envelope,
 * translating failures (including non-2xx HTTP responses) into [ApiException].
 */
suspend fun <T> apiCall(block: suspend () -> ApiEnvelope<T>): T {
    val env = envelope(block)
    if (!env.success) throw env.toException(null)
    @Suppress("UNCHECKED_CAST")
    return env.data as T
}

/** Like [apiCall] but the endpoint may legitimately return `data: null`. */
suspend fun <T> apiCallNullable(block: suspend () -> ApiEnvelope<T>): T? =
    envelope(block).let { env ->
        if (!env.success) throw env.toException(null)
        env.data
    }

suspend fun <T> apiCallPaged(block: suspend () -> ApiEnvelope<T>): Paged<T> {
    val env = envelope(block)
    if (!env.success) throw env.toException(null)
    @Suppress("UNCHECKED_CAST")
    return Paged(env.data as T, env.page)
}

private suspend fun <T> envelope(block: suspend () -> ApiEnvelope<T>): ApiEnvelope<T> =
    try {
        block()
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val parsed = body?.let {
            runCatching { errorJson.decodeFromString<ApiEnvelope<Unit>>(it) }.getOrNull()
        }
        throw ApiException(
            code = parsed?.error?.code ?: "http_${e.code()}",
            message = parsed?.error?.message ?: e.message(),
            detail = parsed?.error?.detail,
            httpStatus = e.code()
        )
    }

private fun ApiEnvelope<*>.toException(status: Int?) = ApiException(
    code = error?.code ?: "unknown_error",
    message = error?.message ?: message ?: "Unknown error",
    detail = error?.detail,
    httpStatus = status
)

/** Human friendly message for any throwable coming from the data layer. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> when (code) {
        "unauthorized" -> "API key inválida o ausente"
        "not_logged_in" -> "Sesión de Telegram no iniciada"
        "setup_required" -> "El servidor necesita completar el asistente de configuración"
        else -> detail?.takeIf { it.isNotBlank() }?.let { "$message — $it" } ?: message
    }
    is java.net.UnknownHostException -> "No se puede resolver el servidor. ¿URL correcta?"
    is java.net.ConnectException -> "No se puede conectar con el servidor"
    is java.net.SocketTimeoutException -> "Tiempo de espera agotado"
    else -> message ?: "Error inesperado"
}
