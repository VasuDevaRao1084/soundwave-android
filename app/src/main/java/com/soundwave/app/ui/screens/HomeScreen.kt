package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.data.Song
import com.soundwave.app.data.UserProfile
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface

private val moodChips = listOf("☀️ Morning", "🎯 Focus", "😌 Chill", "🎉 Party", "💪 Workout", "😴 Sleep")

@Composable
fun HomeScreen(
    user: UserProfile?,
    recentlyPlayed: List<Song>,
    savedAlbums: List<SavedAlbum>,
    recommendedSongs: List<Song>,
    currentSongId: String?,
    likedIds: Set<String>,
    onPlay: (Song) -> Unit,
    onLike: (Song) -> Unit,
    onSearchAlbums: () -> Unit,
    onOpenAlbum: (SavedAlbum) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SwBg),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(52.dp))
            Text(
                "Good ${greeting()}",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = SwPurple, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("All devices synced", color = SwPurple, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Recently played 2-column grid ──────────────────────────────────────
        if (recentlyPlayed.isNotEmpty()) {
            item {
                val gridItems = recentlyPlayed.take(6)
                // Manual 2-column grid inside LazyColumn
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    gridItems.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { song ->
                                RecentSongCard(
                                    song = song,
                                    isPlaying = song.id == currentSongId,
                                    onClick = { onPlay(song) },
                                    modifier = Modifier.weight(1f).padding(4.dp)
                                )
                            }
                            // Fill empty slot if odd number
                            if (row.size == 1) Spacer(Modifier.weight(1f).padding(4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        // ── Made For You (smart recommendations) ───────────────────────────────
        if (recommendedSongs.isNotEmpty()) {
            item {
                Text(
                    "Made For You",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Text(
                    "Based on your recent listening",
                    color = Color(0xFF6B6080),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    recommendedSongs.forEach { song ->
                        RecommendationCard(
                            song = song,
                            isPlaying = song.id == currentSongId,
                            onClick = { onPlay(song) }
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        // ── Your mood ──────────────────────────────────────────────────────────
        item {
            Text(
                "Your mood",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moodChips.forEach { mood ->
                    MoodChip(label = mood)
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        // ── Albums & Artists ───────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Albums & Artists",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onSearchAlbums) {
                    Text("Search +", color = SwPurple, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (savedAlbums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SwSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tap Search + to add albums", color = Color(0xFF6B6080), fontSize = 14.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    savedAlbums.forEach { album ->
                        AlbumCard(album = album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) Color(0xFF2A1F4A) else Color(0xFF15131F))
            .clickable(onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art square on the left
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SwSurface),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail != null) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF4A4560))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            song.title,
            color = if (isPlaying) SwPurple else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
@Composable
private fun RecommendationCard(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isPlaying) SwPurple.copy(alpha = 0.25f) else Color(0xFF1A1730)),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail != null) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF4A4560), modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            color = if (isPlaying) SwPurple else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            color = Color(0xFF6B6080),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MoodChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1730))
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AlbumCard(album: SavedAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1730)),
            contentAlignment = Alignment.Center
        ) {
            if (album.thumbnail != null) {
                AsyncImage(
                    model = album.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF4A4560), modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(album.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = Color(0xFF6B6080), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
