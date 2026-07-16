package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Playlist
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onLike: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onTogglePrivacy: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    friendNewSongsCount: Int = 0,
    onCheckFriendUpdates: () -> Unit = {},
    onMergeFriendUpdates: () -> Unit = {}
) {
    LaunchedEffect(playlist.id) {
        if (playlist.sourceFriendId != null) onCheckFriendUpdates()
    }
    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("${playlist.songs.size} songs", color = Color.Gray, fontSize = 14.sp)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (playlist.isPrivate) Color(0xFF2A1F3D) else Color(0xFF1A1730))
                    .clickable(onClick = onTogglePrivacy)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (playlist.isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                    contentDescription = if (playlist.isPrivate) "Private" else "Public",
                    tint = if (playlist.isPrivate) SwPurple else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (playlist.isPrivate) "Private" else "Public",
                    color = if (playlist.isPrivate) SwPurple else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onDownloadAll) {
                Icon(Icons.Filled.Download, contentDescription = "Download all", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (friendNewSongsCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1F1A35))
                    .clickable(onClick = onMergeFriendUpdates)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = SwPurple, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (friendNewSongsCount == 1) "1 new song since you imported this" else "$friendNewSongsCount new songs since you imported this",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("Tap to add them", color = SwPurple, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
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
