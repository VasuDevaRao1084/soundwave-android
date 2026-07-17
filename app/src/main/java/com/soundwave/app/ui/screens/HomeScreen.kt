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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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

@Composable
private fun ProfileAvatarButton(user: UserProfile?, avatarPath: String?, hasNotification: Boolean = false, onClick: () -> Unit) {
    val localFile = remember(avatarPath) { avatarPath?.let { java.io.File(it) } }
    Box(
        modifier = Modifier.size(40.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(SwPurple, Color(0xFF6C2FF2))))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            when {
                localFile != null && localFile.exists() -> AsyncImage(
                    model = localFile,
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
                user?.avatarUrl != null -> AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
                else -> Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        if (hasNotification) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4B4B))
                    .border(2.dp, SwBg, CircleShape)
            )
        }
    }
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
    onOpenChart: (String, List<Song>) -> Unit,
    avatarPath: String? = null,
    onOpenProfile: () -> Unit = {},
    workoutPlaylist: List<Song> = emptyList(),
    trendingTodayPlaylist: List<Song> = emptyList(),
    hasIncomingFriendRequests: Boolean = false
) {
    // Ambient gradient wash behind the top of the screen — a soft purple glow
    // fading into the flat background, instead of pure black from the very
    // first pixel. This is the single change that makes the most difference
    // to how "alive" the home screen feels (same trick Spotify/Apple Music
    // use behind their own home headers), so everything else keeps its
    // normal SwBg and only this top band gets the tint.
    Box(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF2E1F5C).copy(alpha = 0.55f), SwBg)
                    )
                )
        )
        HomeScreenContent(
            user = user,
            recentlyPlayed = recentlyPlayed,
            savedAlbums = savedAlbums,
            recommendedSongs = recommendedSongs,
            topTelugu = topTelugu,
            topHindi = topHindi,
            topEnglish = topEnglish,
            mostSearchedTelugu = mostSearchedTelugu,
            mostSearchedHindi = mostSearchedHindi,
            mostSearchedEnglish = mostSearchedEnglish,
            currentSongId = currentSongId,
            isAudioPlaying = isAudioPlaying,
            likedIds = likedIds,
            onPlay = onPlay,
            onLike = onLike,
            onSearchAlbums = onSearchAlbums,
            onOpenAlbum = onOpenAlbum,
            onOpenDiagnostics = onOpenDiagnostics,
            onMoodClick = onMoodClick,
            onOpenChart = onOpenChart,
            avatarPath = avatarPath,
            onOpenProfile = onOpenProfile,
            workoutPlaylist = workoutPlaylist,
            trendingTodayPlaylist = trendingTodayPlaylist,
            hasIncomingFriendRequests = hasIncomingFriendRequests
        )
    }
}

@Composable
private fun HomeScreenContent(
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
    onOpenChart: (String, List<Song>) -> Unit,
    avatarPath: String?,
    onOpenProfile: () -> Unit,
    workoutPlaylist: List<Song>,
    trendingTodayPlaylist: List<Song>,
    hasIncomingFriendRequests: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(52.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            ) {
                Text(
                    "Good ${greeting()}",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                ProfileAvatarButton(user = user, avatarPath = avatarPath, hasNotification = hasIncomingFriendRequests, onClick = onOpenProfile)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SwPurple.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = SwPurple, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("All devices synced", color = SwPurple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = "Diagnostics",
                    tint = SwTextTertiary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onOpenDiagnostics)
                )
            }
            Spacer(Modifier.height(24.dp))
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
        } else {
            // Brand new account, no listening history yet — show a featured
            // pick instead of an empty gap where "Jump back in" would be.
            val featuredSong = topTelugu.firstOrNull() ?: topHindi.firstOrNull() ?: topEnglish.firstOrNull()
            if (featuredSong != null) {
                item {
                    FeaturedBanner(song = featuredSong, onClick = { onPlay(featuredSong) })
                    Spacer(Modifier.height(24.dp))
                }
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

        // ── Workout & Trending Today ──────────────────────────────────────────────
        if (workoutPlaylist.isNotEmpty() || trendingTodayPlaylist.isNotEmpty()) {
            item {
                Text(
                    "Workout & Trending",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (workoutPlaylist.isNotEmpty()) {
                        PlaylistCoverCard(
                            line1 = "Workout",
                            line2 = "",
                            coverThumbnail = workoutPlaylist.first().thumbnail,
                            onClick = { onOpenChart("Workout", workoutPlaylist) }
                        )
                    }
                    if (trendingTodayPlaylist.isNotEmpty()) {
                        PlaylistCoverCard(
                            line1 = "Trending",
                            line2 = "Today",
                            coverThumbnail = trendingTodayPlaylist.first().thumbnail,
                            onClick = { onOpenChart("Today's Trending", trendingTodayPlaylist) }
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
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
private fun FeaturedBanner(song: Song, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
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
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1730)))
        }
        // Strong, near-opaque scrim across the whole card — source album art
        // often already has its own baked-in text (movie/artist credits), so
        // a light gradient isn't enough to keep our own title readable and
        // free of visual clash. This fully buries the source image's text
        // while still letting the artwork's color/mood show through.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                "FEATURED",
                color = SwPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(song.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(SwPurple)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play Now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) Color(0xFF241C42) else Color(0xFF15131C))
            .then(
                if (isPlaying)
                    Modifier.border(1.dp, SwPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(SwSurface),
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
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            song.title,
            color = if (isPlaying) SwPurple else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isPlaying) {
            Spacer(Modifier.width(4.dp))
            PlayingWaveform(isPlaying = true, color = SwPurple, barWidth = 2.dp, maxHeight = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
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
