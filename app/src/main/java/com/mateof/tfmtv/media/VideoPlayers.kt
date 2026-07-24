package com.mateof.tfmtv.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.data.prefs.VideoPlayerChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a stream URL over to an installed player. The URL carries the API key
 * as a query parameter, so nothing is downloaded to the device first.
 */
@Singleton
class VideoPlayers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: ServerPreferences
) {
    data class PlayerApp(val packageName: String, val label: String)

    fun installed(): List<PlayerApp> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/*")
        }
        return pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { PlayerApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** @return false when the internal player should take over. */
    fun launchExternal(url: String, title: String): Boolean {
        val choice = prefs.videoPlayer.value
        if (choice.isBlank() || choice == VideoPlayerChoice.INTERNAL) return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val intent = when (choice) {
            VideoPlayerChoice.ASK -> Intent.createChooser(view, "Reproducir con").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            VideoPlayerChoice.SYSTEM -> view
            else -> view.apply { setPackage(choice) }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
