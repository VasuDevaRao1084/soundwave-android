package com.soundwave.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soundwave.app.ui.theme.SwPurple

/**
 * The app's signature visual element — animated waveform bars used as the
 * "now playing" indicator wherever a song row shows the currently playing
 * track. Built from the app's core subject matter (audio/sound) rather
 * than a generic spinner or pulsing dot, so it reads as intentional rather
 * than templated.
 */
@Composable
fun PlayingWaveform(
    isPlaying: Boolean,
    color: Color = SwPurple,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val heights = listOf(0.4f, 1f, 0.6f, 0.85f).mapIndexed { i, base ->
        val anim by transition.animateFloat(
            initialValue = base * 0.3f,
            targetValue = base,
            animationSpec = infiniteRepeatable(
                animation = tween(380 + i * 90, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
        anim
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(maxHeight)
    ) {
        heights.forEach { h ->
            val height = if (isPlaying) maxHeight * h else maxHeight * 0.25f
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
