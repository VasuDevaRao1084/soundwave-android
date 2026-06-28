package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.Song
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPink
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.viewmodel.RepeatMode

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isLiked: Boolean,
    progress: Float,
    duration: Float,
    shuffle: Boolean,
    repeat: RepeatMode,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(SwBg).padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1F1F29)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = song.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(32.dp))
        Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(song.artist, color = Color.Gray, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        Slider(
            value = if (duration > 0) progress / duration else 0f,
            onValueChange = { onSeek(it * duration) },
            colors = SliderDefaults.colors(thumbColor = SwPurple, activeTrackColor = SwPurple, inactiveTrackColor = Color(0xFF333340))
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(progress), color = Color.Gray, fontSize = 12.sp)
            Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) SwPurple else Color.Gray)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeat != RepeatMode.OFF) SwPurple else Color.Gray
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = onLike) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) SwPink else Color.Gray
                )
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    if (seconds.isNaN() || seconds < 0) return "0:00"
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return "$m:${s.toString().padStart(2, '0')}"
}
