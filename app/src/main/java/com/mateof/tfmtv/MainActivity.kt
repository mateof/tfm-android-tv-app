package com.mateof.tfmtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mateof.tfmtv.ui.TfmTvNavHost
import com.mateof.tfmtv.ui.theme.Background
import com.mateof.tfmtv.ui.theme.TfmTvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
