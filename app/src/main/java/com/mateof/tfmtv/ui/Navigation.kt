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
import com.mateof.tfmtv.data.model.LibraryKind
import com.mateof.tfmtv.media.PlayEvent
import com.mateof.tfmtv.ui.screens.channel.ChannelScreen
import com.mateof.tfmtv.ui.screens.gate.GateScreen
import com.mateof.tfmtv.ui.screens.home.HomeScreen
import com.mateof.tfmtv.ui.screens.library.IdentifyScreen
import com.mateof.tfmtv.ui.screens.library.IdentifyTarget
import com.mateof.tfmtv.ui.screens.library.LibraryItemScreen
import com.mateof.tfmtv.ui.screens.player.PlayerScreen
import com.mateof.tfmtv.ui.screens.setup.SetupScreen

object Routes {
    const val GATE = "gate"
    const val SETUP = "setup"
    const val HOME = "home"
    const val CHANNEL = "channel/{channelId}?name={name}"
    const val PLAYER = "player?url={url}&title={title}&channelId={channelId}&fileId={fileId}&startMs={startMs}"
    const val LIBRARY_ITEM = "library/item/{id}"
    const val LIBRARY_IDENTIFY =
        "library/identify?channelId={channelId}&fileId={fileId}&itemId={itemId}&kind={kind}&q={q}&season={season}&episode={episode}"

    fun channel(id: Long, name: String) = "channel/$id?name=${Uri.encode(name)}"

    /** [channelId]/[fileId] identify the file whose progress the player reports; 0/"" disables it. */
    fun player(url: String, title: String, channelId: Long = 0, fileId: String = "", startMs: Long = 0) =
        "player?url=${Uri.encode(url)}&title=${Uri.encode(title)}&channelId=$channelId" +
            "&fileId=${Uri.encode(fileId)}&startMs=$startMs"

    fun player(event: PlayEvent.Internal) =
        player(event.url, event.title, event.channelId, event.fileId, event.startMs)

    fun libraryItem(id: String) = "library/item/${Uri.encode(id)}"

    fun identify(t: IdentifyTarget) =
        "library/identify?channelId=${t.channelId ?: 0}&fileId=${Uri.encode(t.fileId.orEmpty())}" +
            "&itemId=${Uri.encode(t.itemId.orEmpty())}&kind=${t.kind}&q=${Uri.encode(t.query)}" +
            "&season=${t.season ?: -1}&episode=${t.episode ?: -1}"
}

@Composable
fun TfmTvNavHost(modifier: Modifier = Modifier) {
    val nav: NavHostController = rememberNavController()
    val play: (PlayEvent.Internal) -> Unit = { nav.navigate(Routes.player(it)) }
    val identify: (IdentifyTarget) -> Unit = { nav.navigate(Routes.identify(it)) }

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
                onReconfigure = { nav.navigate(Routes.SETUP) },
                onLibraryItem = { nav.navigate(Routes.libraryItem(it)) },
                onIdentify = identify,
                onPlayInternal = play
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
                onPlayInternal = play,
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.LIBRARY_ITEM,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            LibraryItemScreen(
                itemId = entry.arguments?.getString("id").orEmpty(),
                onIdentify = identify,
                onPlayInternal = play,
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.LIBRARY_IDENTIFY,
            arguments = listOf(
                navArgument("channelId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("fileId") { type = NavType.StringType; defaultValue = "" },
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("kind") { type = NavType.StringType; defaultValue = LibraryKind.MOVIE },
                navArgument("q") { type = NavType.StringType; defaultValue = "" },
                navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                navArgument("episode") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            val args = entry.arguments
            val target = IdentifyTarget(
                channelId = args?.getLong("channelId")?.takeIf { it != 0L },
                fileId = args?.getString("fileId")?.takeIf { it.isNotBlank() },
                itemId = args?.getString("itemId")?.takeIf { it.isNotBlank() },
                kind = args?.getString("kind") ?: LibraryKind.MOVIE,
                query = args?.getString("q").orEmpty(),
                season = args?.getInt("season")?.takeIf { it >= 0 },
                episode = args?.getInt("episode")?.takeIf { it >= 0 }
            )
            IdentifyScreen(target = target, onDone = { nav.popBackStack() })
        }

        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("channelId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("fileId") { type = NavType.StringType; defaultValue = "" },
                navArgument("startMs") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { entry ->
            PlayerScreen(
                url = entry.arguments?.getString("url").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                channelId = entry.arguments?.getLong("channelId") ?: 0L,
                fileId = entry.arguments?.getString("fileId").orEmpty(),
                startMs = entry.arguments?.getLong("startMs") ?: 0L,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
