package com.soundwave.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.Song
import com.soundwave.app.ui.theme.SwPink
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean = false,
    isCurrentlyPlaying: Boolean = false, // distinct from isPlaying: this song is loaded AND audio is actively playing (vs paused)
    isLiked: Boolean = false,
    onClick: () -> Unit,
    onLikeClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    val bgColor by animateColorAsState(
        if (isPlaying) Color(0xFF211C38) else Color.Transparent,
        label = "row_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SwSurface),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail != null) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextSecondary, modifier = Modifier.size(24.dp))
            }
            // Signature waveform indicator instead of a generic overlay icon —
            // shows on the currently loaded track, animating only while audio
            // is actually playing (vs. paused, where bars sit lower/static)
            if (isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingWaveform(isPlaying = isCurrentlyPlaying, color = Color.White)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isPlaying) SwPurple else Color.White,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist,
                color = SwTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onLikeClick != null) {
            IconButton(onClick = onLikeClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) SwPink else SwTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = SwTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
