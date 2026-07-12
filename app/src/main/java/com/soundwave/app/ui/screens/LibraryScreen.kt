package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Playlist
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurfaceLight

@Composable
fun LibraryScreen(
    likedSongs: List<Song>,
    playlists: List<Playlist>,
    savedAlbums: List<SavedAlbum>,
    downloadedSongs: List<Song>,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onLike: (Song) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenAlbum: (SavedAlbum) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onRemoveAlbum: (SavedAlbum) -> Unit,
    onSearchAlbums: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val tab = selectedTab
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Text("Library", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))
        TabRow(
            selectedTabIndex = tab,
            containerColor = SwBg,
            contentColor = Color.White
        ) {
            Tab(selected = tab == 0, onClick = { onTabChange(0) }, text = { Text("Liked") })
            Tab(selected = tab == 1, onClick = { onTabChange(1) }, text = { Text("Playlists") })
            Tab(selected = tab == 2, onClick = { onTabChange(2) }, text = { Text("Albums") })
            Tab(selected = tab == 3, onClick = { onTabChange(3) }, text = { Text("Downloads") })
        }
        when (tab) {
            0 -> if (likedSongs.isEmpty()) EmptyState("No liked songs yet") else LazyColumn {
                items(likedSongs) { song ->
                    SongRow(song = song, isPlaying = song.id == currentSongId, isCurrentlyPlaying = song.id == currentSongId && isAudioPlaying, isLiked = true, onClick = { onPlay(song, likedSongs) }, onLikeClick = { onLike(song) }, onMoreClick = { onAddToPlaylist(song) })
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
            1 -> Column {
                TextButton(onClick = { showCreateDialog = true }) { Text("+ New Playlist") }
                if (playlists.isEmpty()) EmptyState("No playlists yet") else LazyColumn {
                    items(playlists) { pl -> PlaylistRow(pl, onClick = { onOpenPlaylist(pl) }, onDelete = { onDeletePlaylist(pl) }) }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
            2 -> Column {
                TextButton(onClick = onSearchAlbums) { Text("+ Find an album") }
                if (savedAlbums.isEmpty()) EmptyState("No saved albums yet") else LazyColumn {
                    items(savedAlbums) { album -> AlbumRow(album, onClick = { onOpenAlbum(album) }, onDelete = { onRemoveAlbum(album) }) }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
            3 -> if (downloadedSongs.isEmpty()) EmptyState("No downloaded songs yet") else LazyColumn {
                items(downloadedSongs) { song ->
                    SongRow(song = song, isPlaying = song.id == currentSongId, isCurrentlyPlaying = song.id == currentSongId && isAudioPlaying, isLiked = false, onClick = { onPlay(song, downloadedSongs) }, onMoreClick = { onAddToPlaylist(song) })
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Playlist name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onCreatePlaylist(name)
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(SwPurple.copy(alpha = 0.85f), Color(0xFF6C2FF2).copy(alpha = 0.85f))
                    ),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            if (playlist.isPrivate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(3.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Private", tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "${playlist.songs.size} songs" + if (playlist.isPrivate) " · Private" else "",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AlbumRow(album: SavedAlbum, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        coil.compose.AsyncImage(
            model = album.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(48.dp).background(SwSurfaceLight, RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(album.name, color = Color.White, fontWeight = FontWeight.Medium)
            Text(album.artist, color = Color.Gray, fontSize = 13.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove album", tint = Color.Gray)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray)
    }
}
