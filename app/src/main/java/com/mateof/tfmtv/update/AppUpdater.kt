package com.mateof.tfmtv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mateof.tfmtv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GhRelease(
    val tag_name: String? = null,
    val body: String? = null,
    val assets: List<GhAsset> = emptyList()
)

@Serializable
private data class GhAsset(
    val name: String? = null,
    val browser_download_url: String? = null,
    val size: Long = 0
)

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long
)

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val info: UpdateInfo) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
}

/**
 * Checks GitHub Releases for a newer APK, downloads it and hands it to the
 * system package installer. Uses its own bare OkHttp client so requests go to
 * api.github.com directly (not through the server host interceptor).
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val repo = "mateof/tfm-android-tv-app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "tfm-android-tv-app")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheck.Error("GitHub respondió ${response.code}")
                }
                val body = response.body?.string()
                    ?: return@withContext UpdateCheck.Error("Respuesta vacía")
                val release = json.decodeFromString<GhRelease>(body)
                val tag = release.tag_name?.removePrefix("v")?.trim().orEmpty()
                val apk = release.assets.firstOrNull {
                    it.name?.endsWith(".apk", ignoreCase = true) == true
                }
                if (tag.isBlank() || apk?.browser_download_url == null) {
                    return@withContext UpdateCheck.Error("La release no tiene APK")
                }
                if (!isNewer(tag, currentVersion)) {
                    return@withContext UpdateCheck.UpToDate
                }
                UpdateCheck.Available(
                    UpdateInfo(
                        versionName = tag,
                        notes = release.body?.trim().orEmpty(),
                        apkUrl = apk.browser_download_url,
                        apkSize = apk.size
                    )
                )
            }
        } catch (e: Exception) {
            UpdateCheck.Error(e.message ?: "Error comprobando actualizaciones")
        }
    }

    /**
     * Downloads the APK reporting progress in 0..100 (or -1 when the total size
     * is unknown). Returns the downloaded file or null on failure.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                val file = File(dir, "tfmtv-${info.versionName}.apk")
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "tfm-android-tv-app")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bodyStream = response.body?.byteStream() ?: return@withContext null
                    val total = response.body?.contentLength() ?: -1L
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastPct = -1
                        while (bodyStream.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            } else {
                                onProgress(-1)
                            }
                        }
                    }
                }
                file
            } catch (e: Exception) {
                null
            }
        }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** @return false when the device has no settings screen for unknown sources. */
    fun requestInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Very small semver-ish comparison: returns true if [remote] > [local]. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val l = local.split(".", "-").mapNotNull { it.toIntOrNull() }
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
