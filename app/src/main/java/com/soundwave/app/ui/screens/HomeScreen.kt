package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Song
import com.soundwave.app.data.UserProfile
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg

@Composable
fun HomeScreen(
    user: UserProfile?,
    recentlyPlayed: List<Song>,
    currentSongId: String?,
    likedIds: Set<String>,
    onPlay: (Song) -> Unit,
    onLike: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Good ${greeting()}${user?.name?.let { ", ${it.split(" ").first()}" } ?: ""}",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(20.dp))
        if (recentlyPlayed.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp)) {
                Text("Search for a song to get started", color = Color.Gray)
            }
        } else {
            Text(
                "Recently played",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn {
                items(recentlyPlayed) { song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        isLiked = likedIds.contains(song.id),
                        onClick = { onPlay(song) },
                        onLikeClick = { onLike(song) }
                    )
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
}
