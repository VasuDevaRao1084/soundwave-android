package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.FriendActivity
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.PlayingWaveform
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary

@Composable
fun FriendActivityScreen(
    activity: List<FriendActivity>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.clickable(onClick = onBack).size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Friend Activity", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("What your friends have been playing", color = SwTextSecondary, fontSize = 12.sp)
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                PlayingWaveform(isPlaying = true, color = SwPurple, barWidth = 4.dp, maxHeight = 28.dp)
            }
            activity.isEmpty() -> Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing to show yet", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Once your friends play something, it'll show up here", color = SwTextSecondary, fontSize = 13.sp)
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(activity, key = { it.friend.id }) { entry ->
                    FriendActivityCard(entry = entry, onPlay = onPlay)
                    Spacer(Modifier.height(14.dp))
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun FriendActivityCard(entry: FriendActivity, onPlay: (Song) -> Unit) {
    val topSong = entry.songs.firstOrNull() ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SwSurface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SwPurple.copy(alpha = 0.7f), Color(0xFF6C2FF2).copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                if (entry.friend.avatarUrl != null) {
                    AsyncImage(
                        model = entry.friend.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    entry.friend.displayName ?: entry.friend.email,
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Text("recently played", color = SwTextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D18))
                .clickable { onPlay(topSong) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1730)),
                contentAlignment = Alignment.Center
            ) {
                if (topSong.thumbnail != null) {
                    AsyncImage(
                        model = topSong.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High
                    )
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(topSong.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(topSong.artist, color = SwTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (entry.songs.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "+ ${entry.songs.size - 1} more recently played",
                color = SwTextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
