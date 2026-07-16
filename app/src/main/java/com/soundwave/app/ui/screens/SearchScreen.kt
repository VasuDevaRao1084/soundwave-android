package com.soundwave.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

private val topSearches = listOf(
    "Trending Telugu", "Arijit Singh", "Glass Animals",
    "AR Rahman", "The Weeknd", "Sid Sriram", "Taylor Swift",
    "Anirudh Ravichander", "Drake", "Dua Lipa"
)

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Song>,
    isSearching: Boolean,
    searchHistory: List<String>,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    likedIds: Set<String>,
    onPlay: (Song) -> Unit,
    onLike: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { onQueryChange(it) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SwBg)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = SwTextSecondary)
                        }
                    }
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search songs, artists, albums...")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            voiceLauncher.launch(intent)
                        } else {
                            Toast.makeText(context, "Voice search isn't available on this device", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = SwPurple)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        when {
            isSearching -> Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                PlayingWaveform(isPlaying = true, color = SwPurple, barWidth = 4.dp, maxHeight = 28.dp)
            }

            query.isBlank() -> {
                // ── Pre-search: recent + top searches ─────────────────────────
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent searches", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = onClearHistory) {
                                    Text("Clear all", color = SwPurple, fontSize = 13.sp)
                                }
                            }
                        }
                        items(searchHistory) { term ->
                            SearchChipRow(
                                label = term,
                                icon = Icons.Filled.History,
                                onClick = { onQueryChange(term) }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    item {
                        Text(
                            "Popular searches",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                    items(topSearches) { term ->
                        SearchChipRow(
                            label = term,
                            icon = Icons.Filled.TrendingUp,
                            onClick = { onQueryChange(term) }
                        )
                    }
                    item { Spacer(Modifier.height(120.dp)) }
                }
            }

            results.isEmpty() -> EmptyState(
                title = "No results found",
                subtitle = "Try a different search term"
            )

            else -> AnimatedVisibility(visible = true, enter = fadeIn(tween(250))) {
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

@Composable
private fun SearchChipRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SwSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SwTextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        Modifier.fillMaxWidth().padding(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlayingWaveform(isPlaying = false, color = SwTextTertiary, barWidth = 4.dp, maxHeight = 24.dp)
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = SwTextSecondary, fontSize = 13.sp)
        }
    }
}
