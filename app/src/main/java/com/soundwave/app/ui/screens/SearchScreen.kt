package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Song>,
    isSearching: Boolean,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    likedIds: Set<String>,
    onPlay: (Song) -> Unit,
    onLike: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwBg)
    ) {
        Spacer(Modifier.height(52.dp))

        // Header
        Text(
            "Search",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(16.dp))

        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Songs, artists, albums...", color = Color(0xFF4A4560)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SwPurple) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF6B6080))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SwSurface,
                unfocusedContainerColor = SwSurface,
                focusedBorderColor = SwPurple,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = SwPurple
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        when {
            isSearching -> Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SwPurple, strokeWidth = 2.dp)
            }
            results.isEmpty() && query.isNotBlank() -> Box(
                Modifier.fillMaxWidth().padding(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No results found", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Try a different search term", color = Color(0xFF6B6080), fontSize = 13.sp)
                }
            }
            results.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Find your music", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Search for any song or artist", color = Color(0xFF6B6080), fontSize = 13.sp)
                }
            }
            else -> {
                Text(
                    "${results.size} results",
                    color = Color(0xFF6B6080),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(results) { song ->
                        SongRow(
                            song = song,
                            isPlaying = song.id == currentSongId,
                            isCurrentlyPlaying = song.id == currentSongId && isAudioPlaying,
                            isLiked = likedIds.contains(song.id),
                            onClick = { onPlay(song) },
                            onLikeClick = { onLike(song) },
                            onMoreClick = { onAddToPlaylist(song) }
                        )
                    }
                    item { Spacer(Modifier.height(120.dp)) }
                }
            }
        }
    }
}
