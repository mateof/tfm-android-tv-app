package com.mateof.tfmtv.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mateof.tfmtv.ui.screens.channel.ChannelScreen
import com.mateof.tfmtv.ui.screens.gate.GateScreen
import com.mateof.tfmtv.ui.screens.home.HomeScreen
import com.mateof.tfmtv.ui.screens.player.PlayerScreen
import com.mateof.tfmtv.ui.screens.setup.SetupScreen

object Routes {
    const val GATE = "gate"
    const val SETUP = "setup"
    const val HOME = "home"
    const val CHANNEL = "channel/{channelId}?name={name}"
    const val PLAYER = "player?url={url}&title={title}"

    fun channel(id: Long, name: String) = "channel/$id?name=${Uri.encode(name)}"
    fun player(url: String, title: String) =
        "player?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
}

@Composable
fun TfmTvNavHost(modifier: Modifier = Modifier) {
    val nav: NavHostController = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.GATE, modifier = modifier) {

        composable(Routes.GATE) {
            GateScreen(
                onSetup = { nav.navigate(Routes.SETUP) { popUpTo(Routes.GATE) { inclusive = true } } },
                onReady = { nav.navigate(Routes.HOME) { popUpTo(Routes.GATE) { inclusive = true } } }
            )
        }

        composable(Routes.SETUP) {
            SetupScreen(
                onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } } }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onChannel = { id, name -> nav.navigate(Routes.channel(id, name)) },
                onReconfigure = { nav.navigate(Routes.SETUP) }
            )
        }

        composable(
            Routes.CHANNEL,
            arguments = listOf(
                navArgument("channelId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            ChannelScreen(
                channelId = entry.arguments?.getLong("channelId") ?: 0L,
                channelName = entry.arguments?.getString("name").orEmpty(),
                onPlayInternal = { url, title -> nav.navigate(Routes.player(url, title)) },
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            PlayerScreen(
                url = entry.arguments?.getString("url").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                onBack = { nav.popBackStack() }
            )
        }
    }
}
