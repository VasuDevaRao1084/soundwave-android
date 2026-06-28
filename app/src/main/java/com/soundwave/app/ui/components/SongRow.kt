package com.soundwave.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
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
fun SongRow(
    song: Song,
    isPlaying: Boolean = false,
    isLiked: Boolean = false,
    onClick: () -> Unit,
    onLikeClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SwSurfaceLight)
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isPlaying) Color(0xFF8B5CF6) else Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = SwTextSecondary,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onLikeClick != null) {
            IconButton(onClick = onLikeClick) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFFF472B6) else SwTextSecondary
                )
            }
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = SwTextSecondary)
            }
        }
    }
}
