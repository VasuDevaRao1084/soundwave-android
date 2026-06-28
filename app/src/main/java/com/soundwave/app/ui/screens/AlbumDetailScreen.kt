package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.SaavnApi
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg

@Composable
fun AlbumDetailScreen(
    album: SavedAlbum,
    currentSongId: String?,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onLike: (Song) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(album.id) {
        loading = true
        songs = try { SaavnApi.getAlbumSongs(album.id) } catch (e: Exception) { emptyList() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column {
                Text(album.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(album.artist, color = Color.Gray, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(40.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            songs.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp)) {
                Text("Couldn't load this album", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
            else -> LazyColumn {
                items(songs) { song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        isLiked = likedIds.contains(song.id),
                        onClick = { onPlay(song, songs) },
                        onLikeClick = { onLike(song) }
                    )
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}
