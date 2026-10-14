package com.example.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memory.game.GameController
import com.example.memory.game.GameMode

@Composable
fun GameScreen(game: GameController, onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(game, onExit)

            if (game.isOnline && !game.isWon) {
                TurnBanner(myTurn = game.isMyTurn)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(game.cards, key = { it.id }) { card ->
                    MemoryCard(
                        card = card,
                        onClick = { game.onCardClick(card.id) },
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = game.isWon,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            WinOverlay(game, onExit)
        }
    }
}

@Composable
private fun TopBar(game: GameController, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphButton(glyph = "←", onClick = onExit)

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (game.mode == GameMode.VERSUS) {
                PlayerChip(
                    name = playerLabel(game, 1),
                    pairs = game.player1Pairs,
                    active = game.currentPlayer == 1 && !game.isWon,
                    modifier = Modifier.weight(1f),
                )
                PlayerChip(
                    name = playerLabel(game, 2),
                    pairs = game.player2Pairs,
                    active = game.currentPlayer == 2 && !game.isWon,
                    modifier = Modifier.weight(1f),
                )
            } else {
                StatChip(label = "Время", value = formatTime(game.elapsedSeconds), modifier = Modifier.weight(1f))
                StatChip(label = "Ходы", value = game.moves.toString(), modifier = Modifier.weight(1f))
                StatChip(label = "Пары", value = "${game.matchedPairs}/${game.totalPairs}", modifier = Modifier.weight(1f))
            }
        }

        // Reshuffling mid-game would desync peers, so hide restart online.
        if (!game.isOnline) {
            GlyphButton(glyph = "⟳", onClick = { game.restart() })
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }
}

private fun playerLabel(game: GameController, player: Int): String {
    val base = if (player == 1) game.player1Name else game.player2Name
    return if (game.isOnline && game.myPlayer == player) "$base (вы)" else base
}

@Composable
private fun TurnBanner(myTurn: Boolean) {
    val bg = if (myTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (myTurn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (myTurn) "Ваш ход" else "Ход соперника…",
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Score panel for one player; glows when it's their turn. */
@Composable
private fun PlayerChip(
    name: String,
    pairs: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val onContainer = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(
                width = if (active) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (active) "▸ $name" else name,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = onContainer.copy(alpha = if (active) 1f else 0.7f),
            maxLines = 1,
        )
        Text(
            text = pairs.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = onContainer,
        )
    }
}

@Composable
private fun WinOverlay(game: GameController, onExit: () -> Unit) {
    val versus = game.mode == GameMode.VERSUS
    val title: String
    val subtitle: String
    if (versus) {
        title = when (game.winner) {
            1 -> "${game.player1Name} победил!"
            2 -> "${game.player2Name} победил!"
            else -> "Ничья!"
        }
        subtitle = "${game.player1Name} — ${game.player1Pairs}   ·   ${game.player2Name} — ${game.player2Pairs}"
    } else {
        title = "Победа!"
        subtitle = "Время ${formatTime(game.elapsedSeconds)} · ${game.moves} ходов"
    }
    val emoji = if (versus && game.winner == 0) "🤝" else "🎉"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(40.dp),
        ) {
            Text(emoji, fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(28.dp))

            // Rematch isn't coordinated over the network yet, so online games
            // only offer a return to the lobby.
            if (!game.isOnline) {
                OverlayButton(
                    text = "Ещё раз",
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    onClick = { game.restart() },
                )
                Spacer(Modifier.height(12.dp))
            }
            OverlayButton(
                text = if (game.isOnline) "В лобби" else "В меню",
                container = if (game.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                content = if (game.isOnline) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun OverlayButton(
    text: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
