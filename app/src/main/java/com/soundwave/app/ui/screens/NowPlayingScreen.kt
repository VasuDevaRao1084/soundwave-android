package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.LyricsApi
import com.soundwave.app.data.LyricsResult
import com.soundwave.app.data.Song
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPink
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.extractAlbumTheme
import com.soundwave.app.viewmodel.RepeatMode
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isLiked: Boolean,
    isDownloaded: Boolean,
    progress: Float,
    duration: Float,
    shuffle: Boolean,
    repeat: RepeatMode,
    sleepTimerMins: Int?,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleDownload: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit
) {
    val context = LocalContext.current
    var primaryColor by remember(song.id) { mutableStateOf(SwPurple) }
    var darkColor by remember(song.id) { mutableStateOf(Color(0xFF0D0B18)) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var lyrics by remember(song.id) { mutableStateOf<LyricsResult?>(null) }
    var lyricsLoading by remember(song.id) { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        extractAlbumTheme(context, song.thumbnail)?.let {
            primaryColor = it.primary
            darkColor = it.dark
        }
    }
    LaunchedEffect(song.id) {
        lyricsLoading = true
        lyrics = try { LyricsApi.fetch(song.title, song.artist, song.durationSec) } catch (e: Exception) { null }
        lyricsLoading = false
    }

    val activeLyricIdx = lyrics?.synced?.let { lines ->
        lines.indexOfLast { it.timeSec <= progress }
    } ?: -1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(darkColor, SwBg, SwBg),
                    startY = 0f, endY = 1400f
                )
            )
            // Blocks all touch events from passing through to content below
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Top bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close",
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(
                    "NOW PLAYING",
                    color = Color(0xFF9A8FBF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                IconButton(onClick = { /* queue */ }) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "Queue",
                        tint = Color(0xFF6B6080), modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Album art with swipe gesture ───────────────────────────────────
            if (showLyrics) {
                LyricsPanel(
                    lyrics = lyrics,
                    loading = lyricsLoading,
                    activeLineIdx = activeLyricIdx,
                    onSeek = onSeek,
                    primaryColor = primaryColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(40.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1A1730))
                        .pointerInput(Unit) {
                            // Swipe left = next, swipe right = previous
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (abs(dragAmount) > 50f) {
                                    if (dragAmount < 0) onNext() else onPrevious()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "SWIPE TO SKIP",
                color = Color(0xFF3A3550),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(16.dp))

            // ── Title + artist ─────────────────────────────────────────────────
            Text(
                song.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(song.artist)
                    if (!song.album.isNullOrBlank()) append(" • ${song.album}")
                },
                color = Color(0xFF6B6080),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // ── Action row: heart, lyrics, download, sleep ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIcon(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    tint = if (isLiked) SwPink else Color(0xFF4A4560),
                    onClick = onLike
                )
                ActionIcon(
                    icon = Icons.Filled.Lyrics,
                    tint = if (showLyrics) primaryColor else Color(0xFF4A4560),
                    onClick = { showLyrics = !showLyrics }
                )
                ActionIcon(
                    icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    tint = if (isDownloaded) primaryColor else Color(0xFF4A4560),
                    onClick = onToggleDownload
                )
                ActionIcon(
                    icon = Icons.Filled.Bedtime,
                    tint = if (sleepTimerMins != null) primaryColor else Color(0xFF4A4560),
                    onClick = { showSleepMenu = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Seek bar ───────────────────────────────────────────────────────
            Slider(
                value = if (duration > 0) (progress / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeek(it * duration) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0xFF2E2A42)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(progress), color = Color(0xFF6B6080), fontSize = 12.sp)
                Text(formatTime(duration), color = Color(0xFF6B6080), fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            // ── Playback controls ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle",
                        tint = if (shuffle) primaryColor else Color(0xFF4A4560),
                        modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }
                // Big purple play button — exactly like web app
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(20.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Color(0xFFB06EF5), SwPurple))
                        )
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeat != RepeatMode.OFF) primaryColor else Color(0xFF4A4560),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (showSleepMenu) {
            SleepTimerMenu(
                current = sleepTimerMins,
                onDismiss = { showSleepMenu = false },
                onSelect = { onSetSleepTimer(it); showSleepMenu = false }
            )
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SleepTimerMenu(current: Int?, onDismiss: () -> Unit, onSelect: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1B2E),
        titleContentColor = Color.White,
        title = { Text("Sleep timer", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { mins ->
                    TextButton(onClick = { onSelect(mins) }) {
                        Text("$mins minutes", color = if (current == mins) SwPurple else Color.White)
                    }
                }
                if (current != null) {
                    TextButton(onClick = { onSelect(null) }) { Text("Turn off", color = SwPink) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF9A8FBF)) } }
    )
}

@Composable
private fun LyricsPanel(
    lyrics: LyricsResult?,
    loading: Boolean,
    activeLineIdx: Int,
    onSeek: (Float) -> Unit,
    primaryColor: Color
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(activeLineIdx) {
        if (activeLineIdx >= 0) scope.launch { listState.animateScrollToItem((activeLineIdx - 2).coerceAtLeast(0)) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF13111F))
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryColor)
            }
            lyrics?.instrumental == true -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🎵 Instrumental", color = Color(0xFF6B6080))
            }
            lyrics?.synced != null -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(20.dp)) {
                items(lyrics.synced.size) { i ->
                    Text(
                        lyrics.synced[i].text.ifBlank { "···" },
                        color = if (i == activeLineIdx) Color.White else Color(0xFF3A3550),
                        fontWeight = if (i == activeLineIdx) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (i == activeLineIdx) 20.sp else 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            .clickable { onSeek(lyrics.synced[i].timeSec.toFloat()) }
                    )
                }
            }
            lyrics?.plain != null -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text(lyrics.plain, color = Color(0xFF9A8FBF), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lyrics found", color = Color(0xFF6B6080))
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    if (seconds.isNaN() || seconds < 0) return "0:00"
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return "$m:${s.toString().padStart(2, '0')}"
}
