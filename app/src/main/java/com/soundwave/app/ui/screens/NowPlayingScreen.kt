package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var darkColor by remember(song.id) { mutableStateOf(SwBg) }
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
            .background(Brush.verticalGradient(listOf(darkColor.copy(alpha = 0.6f), SwBg)))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
                }
                Row {
                    IconButton(onClick = { showSleepMenu = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerMins != null) primaryColor else Color.Gray
                        )
                    }
                    IconButton(onClick = onToggleDownload) {
                        Icon(
                            if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = "Download",
                            tint = if (isDownloaded) primaryColor else Color.Gray
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

            Spacer(Modifier.height(16.dp))

            if (showLyrics) {
                LyricsPanel(
                    lyrics = lyrics,
                    loading = lyricsLoading,
                    activeLineIdx = activeLyricIdx,
                    onSeek = onSeek,
                    onClose = { showLyrics = false }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F1F29)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(model = song.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(song.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = if (showLyrics) primaryColor else Color.Gray)
                }
            }
            Spacer(Modifier.height(16.dp))

            Slider(
                value = if (duration > 0) (progress / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeek(it * duration) },
                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = Color(0xFF333340))
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(progress), color = Color.Gray, fontSize = 12.sp)
                Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) primaryColor else Color.Gray)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(32.dp)
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat", tint = if (repeat != RepeatMode.OFF) primaryColor else Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like", tint = if (isLiked) SwPink else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerMenu(current: Int?, onDismiss: () -> Unit, onSelect: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { mins ->
                    TextButton(onClick = { onSelect(mins) }) { Text("$mins minutes") }
                }
                if (current != null) {
                    TextButton(onClick = { onSelect(null) }) { Text("Turn off") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LyricsPanel(
    lyrics: LyricsResult?,
    loading: Boolean,
    activeLineIdx: Int,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeLineIdx) {
        if (activeLineIdx >= 0) {
            scope.launch { listState.animateScrollToItem((activeLineIdx - 2).coerceAtLeast(0)) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A22))
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lyrics?.instrumental == true -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🎵 Instrumental track", color = Color.Gray)
            }
            lyrics?.synced != null -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(lyrics.synced.size) { i ->
                    val line = lyrics.synced[i]
                    Text(
                        line.text.ifBlank { "···" },
                        color = if (i == activeLineIdx) Color.White else Color.Gray,
                        fontWeight = if (i == activeLineIdx) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (i == activeLineIdx) 18.sp else 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }
            lyrics?.plain != null -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text(lyrics.plain, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lyrics found for this song", color = Color.Gray)
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
