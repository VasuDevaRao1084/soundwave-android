package com.soundwave.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.soundwave.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.data.Song
import com.soundwave.app.data.UserProfile
import com.soundwave.app.ui.components.PlayingWaveform
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary
import com.soundwave.app.ui.theme.extractAlbumTheme

// JioSaavn's API is a plain text search, not a curated mood/playlist system —
// so each slot currently maps to a keyword search likely to surface matching
// songs (titles/albums literally using these words), rather than an
// editorially curated playlist like Spotify's. This is a placeholder until
// real JioSaavn playlist tokens (like the Charts tokens) are wired in per
// slot — swap the `query` values for `getPlaylistSongs(token)` calls once
// real playlist links are provided.
private data class TimeSlot(val label: String, val emoji: String, val query: String, val startHour: Int, val endHour: Int)

private val timeSlots = listOf(
    TimeSlot("Morning", "☀️", "morning melody", startHour = 5, endHour = 11),
    TimeSlot("Afternoon", "🌤️", "afternoon feel good", startHour = 11, endHour = 17),
    TimeSlot("Evening", "🌇", "evening chill", startHour = 17, endHour = 21),
    TimeSlot("Night", "🌙", "late night vibes", startHour = 21, endHour = 5)
)

private fun currentTimeSlotIndex(): Int {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    timeSlots.forEachIndexed { i, slot ->
        val inRange = if (slot.startHour < slot.endHour) hour in slot.startHour until slot.endHour
        else hour >= slot.startHour || hour < slot.endHour // wraps past midnight (Night: 21–5)
        if (inRange) return i
    }
    return 0
}

@Composable
fun HomeScreen(
    user: UserProfile?,
    recentlyPlayed: List<Song>,
    savedAlbums: List<SavedAlbum>,
    recommendedSongs: List<Song>,
    topTelugu: List<Song>,
    topHindi: List<Song>,
    topEnglish: List<Song>,
    mostSearchedTelugu: List<Song>,
    mostSearchedHindi: List<Song>,
    mostSearchedEnglish: List<Song>,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    likedIds: Set<String>,
    onPlay: (Song) -> Unit,
    onLike: (Song) -> Unit,
    onSearchAlbums: () -> Unit,
    onOpenAlbum: (SavedAlbum) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onMoodClick: (String, String) -> Unit,
    onOpenChart: (String, List<Song>) -> Unit
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
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = SwPurple, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("All devices synced", color = SwPurple, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = "Diagnostics",
                    tint = SwTextTertiary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onOpenDiagnostics)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Hero card: jump back into the most recent song ──────────────────────
        // The page's single most characteristic moment — a large, color-themed
        // card pulling its gradient from the actual album art, not a generic
        // banner. Replaces the old flat 2-column grid's top slot.
        if (recentlyPlayed.isNotEmpty()) {
            item {
                HeroCard(
                    song = recentlyPlayed.first(),
                    isPlaying = recentlyPlayed.first().id == currentSongId,
                    isAudioPlaying = isAudioPlaying,
                    onClick = { onPlay(recentlyPlayed.first()) }
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Recently played 2-column grid (rest of the list) ────────────────────
        if (recentlyPlayed.size > 1) {
            item {
                Text(
                    "Recently played",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(10.dp))
                val gridItems = recentlyPlayed.drop(1).take(5)
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
                    color = SwTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendedSongs, key = { it.id }) { song ->
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
                "Right Now",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            val activeIndex = remember { currentTimeSlotIndex() }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                timeSlots.forEachIndexed { index, slot ->
                    TimeSlotTile(
                        slot = slot,
                        isNow = index == activeIndex,
                        onClick = { onMoodClick(slot.label, slot.query) }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        // ── Charts: unified playlist-cover row (Spotify-style) ───────────────────
        // One big cover card per language chart instead of 3 separate rows of
        // 10 song cards each — the #1 song's own art becomes the playlist
        // cover, with the chart name overlaid, same pattern as Spotify's
        // "Latest Telugu" edicorial covers.
        if (topTelugu.isNotEmpty() || topHindi.isNotEmpty() || topEnglish.isNotEmpty()) {
            item {
                Text(
                    "Charts",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val charts = listOfNotNull(
                        if (topTelugu.isNotEmpty()) Triple("Top", "Telugu", topTelugu) else null,
                        if (topHindi.isNotEmpty()) Triple("Top", "Hindi", topHindi) else null,
                        if (topEnglish.isNotEmpty()) Triple("Top", "English", topEnglish) else null
                    )
                    items(charts, key = { it.second }) { (line1, line2, songs) ->
                        PlaylistCoverCard(
                            line1 = line1,
                            line2 = line2,
                            coverThumbnail = songs.first().thumbnail,
                            onClick = { onOpenChart("$line1 $line2", songs) }
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        // ── Top charts by language (Most Searched only from here) ───────────────
        // Ranked by JioSaavn's real play_count field — genuinely the most-played
        // matching songs, not a random or hand-picked list.
        listOf(
            Triple("Most Searched Telugu", "🔎", mostSearchedTelugu) to false,
            Triple("Most Searched Hindi", "🔎", mostSearchedHindi) to false,
            Triple("Most Searched English", "🔎", mostSearchedEnglish) to false
        ).forEach { (triple, isRanked) ->
            val (title, emoji, songs) = triple
            if (songs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clickable { onOpenChart(title, songs) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$emoji $title",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("See all ${songs.size} →", color = SwPurple, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(songs.take(10), key = { _, song -> song.id }) { index, song ->
                            CompactChartChip(
                                song = song,
                                isPlaying = song.id == currentSongId,
                                onClick = { onPlay(song) }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
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
                    Text("Tap Search + to add albums", color = SwTextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedAlbums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(song: Song, isPlaying: Boolean, isAudioPlaying: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var themeColor by remember(song.id) { mutableStateOf(SwPurple) }
    var darkColor by remember(song.id) { mutableStateOf(Color(0xFF1A1730)) }

    LaunchedEffect(song.id) {
        extractAlbumTheme(context, song.thumbnail)?.let {
            themeColor = it.primary
            darkColor = it.dark
        }
    }

    // Subtle entrance: scale + fade in once on first composition
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(450)) + scaleIn(initialScale = 0.96f, animationSpec = tween(450))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(160.dp)
                .shadow(20.dp, RoundedCornerShape(20.dp), spotColor = themeColor)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(listOf(darkColor, darkColor.copy(alpha = 0.6f)))
                )
                .clickable(onClick = onClick)
        ) {
            // Album art bleeds off the right edge, faded into the gradient
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(160.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(160.dp)
                    .background(Brush.horizontalGradient(listOf(darkColor, Color.Transparent)))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
                    .fillMaxWidth(0.62f)
            ) {
                Text(
                    "JUMP BACK IN",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    song.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = darkColor, modifier = Modifier.size(20.dp))
                    }
                    if (isPlaying) {
                        Spacer(Modifier.width(12.dp))
                        PlayingWaveform(isPlaying = isAudioPlaying, color = Color.White, barWidth = 3.dp, maxHeight = 16.dp)
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
            .background(if (isPlaying) Color(0xFF221C38) else Color(0xFF15131C))
            .clickable(onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary)
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
private fun RecommendationCard(song: Song, isPlaying: Boolean, rank: Int? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPlaying) SwPurple.copy(alpha = 0.25f) else Color(0xFF1A1730)),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail != null) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary, modifier = Modifier.size(30.dp))
            }
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "#$rank",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
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
            color = SwTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCoverCard(line1: String, line2: String, coverThumbnail: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1730))
            .clickable(onClick = onClick)
    ) {
        if (coverThumbnail != null) {
            AsyncImage(
                model = coverThumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High
            )
        }
        // Dark scrim so the overlaid text stays readable over any album art.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 60f
                    )
                )
        )
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(line1, color = Color(0xFFFFE14D), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
            Text(line2, color = Color(0xFFFFE14D), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun CompactChartChip(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = if (isPlaying) SolidColor(SwPurple.copy(alpha = 0.20f))
                else Brush.horizontalGradient(listOf(Color(0xFF241B3A), Color(0xFF17132A))),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1730)),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail != null) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                color = SwTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun timeSlotGradient(label: String): List<Color> = when (label) {
    "Morning" -> listOf(Color(0xFFFFB347), Color(0xFFFF6F61))
    "Afternoon" -> listOf(Color(0xFF4FACFE), Color(0xFF00C6A2))
    "Evening" -> listOf(Color(0xFFFF7E5F), Color(0xFF7B2FF7))
    else -> listOf(Color(0xFF16213E), Color(0xFF0F0C29)) // Night
}

@Composable
private fun TimeSlotTile(slot: TimeSlot, isNow: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 128.dp, height = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(timeSlotGradient(slot.label)))
            .then(
                if (isNow) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(slot.emoji, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopStart))
        Text(
            slot.label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        if (isNow) {
            Text(
                "NOW",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
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
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(album.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = SwTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
