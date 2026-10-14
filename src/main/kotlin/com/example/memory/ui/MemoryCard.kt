package com.example.memory.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memory.model.Card
import com.example.memory.ui.theme.cardBackColorsDark
import com.example.memory.ui.theme.cardBackColorsLight

/**
 * A single flippable memory card. Uses a Y-axis rotation so the back and
 * front swap at the 90° midpoint, giving a real "flip" feel.
 */
@Composable
fun MemoryCard(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val faceUp = card.isFaceUp || card.isMatched
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "flip",
    )
    val matchedAlpha by animateFloatAsState(
        targetValue = if (card.isMatched) 0.5f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "matched",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
                alpha = matchedAlpha
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !card.isMatched && !card.isFaceUp) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (rotation <= 90f) {
            CardBack()
        } else {
            // Counter-rotate the front so the emoji isn't mirrored.
            CardFront(
                emoji = card.emoji,
                modifier = Modifier.graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
private fun CardBack() {
    val colors = if (isSystemInDarkTheme()) cardBackColorsDark else cardBackColorsLight
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
            .border(1.dp, colors.emblem, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✦",
            color = colors.emblem,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CardFront(emoji: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 32.sp,
            textAlign = TextAlign.Center,
        )
    }
}
