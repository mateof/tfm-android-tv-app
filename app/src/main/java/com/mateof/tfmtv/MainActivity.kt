package com.mateof.tfmtv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mateof.tfmtv.cast.CastPanelRequests
import com.mateof.tfmtv.ui.TfmTvNavHost
import com.mateof.tfmtv.ui.theme.Background
import com.mateof.tfmtv.ui.theme.TfmTvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            TfmTvTheme {
                TfmTvNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background)
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
