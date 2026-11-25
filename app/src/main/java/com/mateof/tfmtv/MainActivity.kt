package com.mateof.tfmtv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateof.tfmtv.cast.CastPanelRequests
import com.mateof.tfmtv.ui.TfmTvNavHost
import com.mateof.tfmtv.data.prefs.ServerPreferences
import com.mateof.tfmtv.ui.theme.TfmTvTheme
import com.mateof.tfmtv.ui.theme.appBackground
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: ServerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val accent by prefs.accent.collectAsStateWithLifecycle()
            val effects by prefs.effects.collectAsStateWithLifecycle()
            TfmTvTheme(accent, effects) {
                TfmTvNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .appBackground()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The send notification opens the transmission panel. */
    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CAST, false) == true) CastPanelRequests.request()
    }

    companion object {
        const val EXTRA_OPEN_CAST = "open_cast_panel"
    }
}
