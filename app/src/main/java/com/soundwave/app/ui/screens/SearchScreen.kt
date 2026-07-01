package com.soundwave.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.PlayingWaveform
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary

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

        Text(
            "Search",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Songs, artists, albums...", color = SwTextTertiary) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SwPurple) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = SwTextSecondary)
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
                // Waveform doubles as the loading indicator — searching reads
                // as "listening for matches" instead of a generic spinner
                PlayingWaveform(isPlaying = true, color = SwPurple, barWidth = 4.dp, maxHeight = 28.dp)
            }
            results.isEmpty() && query.isNotBlank() -> EmptyState(
                title = "No results found",
                subtitle = "Try a different search term"
            )
            results.isEmpty() -> EmptyState(
                title = "Find your music",
                subtitle = "Search for any song or artist"
            )
            else -> {
                AnimatedVisibility(visible = true, enter = fadeIn(tween(250))) {
                    Column {
                        Text(
                            "${results.size} results",
                            color = SwTextSecondary,
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
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        Modifier.fillMaxWidth().padding(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Static waveform bars (not animated) signal "idle" rather than "loading"
            PlayingWaveform(isPlaying = false, color = SwTextTertiary, barWidth = 4.dp, maxHeight = 24.dp)
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = SwTextSecondary, fontSize = 13.sp)
        }
    }
}
