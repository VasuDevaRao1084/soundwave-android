package com.soundwave.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soundwave.app.data.Song
import com.soundwave.app.ui.theme.SwSurfaceLight
import com.soundwave.app.ui.theme.SwTextSecondary

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SwSurfaceLight)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))) {
            AsyncImage(model = song.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = SwTextSecondary, fontSize = MaterialTheme.typography.bodySmall.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onTogglePlay) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = Color.White)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
        }
    }
}
