package com.example.memory.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.memory.game.GameController
import com.example.memory.net.NetworkManager

private enum class Route { HOME, LOCAL_GAME, NET_NICK, NET_LOBBY, NET_GAME }

@Composable
fun MemoryApp() {
    val scope = rememberCoroutineScope()
    val game = remember { GameController(scope) }
    val net = remember { NetworkManager(scope) }
    var route by remember { mutableStateOf(Route.HOME) }

    DisposableEffect(Unit) {
        // Mirror local taps to the opponent; apply the opponent's taps locally.
        game.onLocalTap = { net.sendTap(it) }
        net.onTap = { game.onRemoteTap(it) }

        // Host builds the shared deck for the currently selected difficulty.
        net.deckProvider = { game.buildDeck(game.difficulty.pairs) }

        net.onGameStart = { deck, myPlayer, hostNick, guestNick ->
            game.startOnlineGame(deck, myPlayer, hostNick, guestNick)
            route = Route.NET_GAME
        }
        net.onOpponentLeft = {
            game.stop()
            if (route == Route.NET_GAME) route = Route.NET_LOBBY
        }

        onDispose {
            game.stop()
            net.stop()
        }
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "route",
    ) { r ->
        when (r) {
            Route.HOME -> HomeScreen(
                game = game,
                onPlayLocal = {
                    game.startGame()
                    route = Route.LOCAL_GAME
                },
                onPlayOnline = { route = Route.NET_NICK },
            )

            Route.LOCAL_GAME -> GameScreen(
                game = game,
                onExit = {
                    game.stop()
                    route = Route.HOME
                },
            )

            Route.NET_NICK -> NicknameScreen(
                onReady = { nick ->
                    net.start(nick)
                    route = Route.NET_LOBBY
                },
                onBack = { route = Route.HOME },
            )

            Route.NET_LOBBY -> LobbyScreen(
                net = net,
                onBack = {
                    net.stop()
                    route = Route.HOME
                },
            )

            Route.NET_GAME -> GameScreen(
                game = game,
                onExit = {
                    game.stop()
                    net.leaveGame()
                    route = Route.NET_LOBBY
                },
            )
        }
    }
}
