package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Playlist
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onLike: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column {
                Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${playlist.songs.size} songs", color = Color.Gray, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (playlist.songs.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp)) {
                Text("No songs in this playlist yet", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn {
                items(playlist.songs) { song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        isCurrentlyPlaying = song.id == currentSongId && isAudioPlaying,
                        isLiked = likedIds.contains(song.id),
                        onClick = { onPlay(song, playlist.songs) },
                        onLikeClick = { onLike(song) },
                        onMoreClick = { onRemoveSong(song) }
                    )
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}
