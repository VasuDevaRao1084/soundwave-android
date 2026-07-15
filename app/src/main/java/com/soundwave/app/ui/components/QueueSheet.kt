package com.soundwave.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.soundwave.app.data.Song
import com.soundwave.app.ui.theme.SwPurple
import kotlin.math.roundToInt

// Fixed row height so drag math (how many rows a drag distance corresponds
// to) is exact rather than relying on measuring each composed row, which
// would be one frame late and jittery.
private val QUEUE_ROW_HEIGHT: Dp = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentSongId: String?,
    queueIndex: Int,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { QUEUE_ROW_HEIGHT.toPx() }

    // Local mirror of the queue so a drag can reorder rows instantly without
    // waiting on a round-trip through the ViewModel/StateFlow. Resyncs
    // whenever the real queue changes underneath us (new song started, a
    // background window refresh landed, etc.) as long as we're not mid-drag.
    var items by remember { mutableStateOf(queue) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(queue) {
        if (draggingIndex == -1) items = queue
    }

    LaunchedEffect(queueIndex) {
        if (queueIndex >= 0) {
            listState.animateScrollToItem((queueIndex - 1).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13111F),
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3A3550))
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Queue", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${items.size} songs", color = Color(0xFF6B6080), fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF6B6080))
                }
            }

            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Queue is empty", color = Color(0xFF6B6080))
                }
            } else {
                Text(
                    "NOW PLAYING",
                    color = Color(0xFF6B6080),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(items, key = { _, song -> song.id }) { idx, song ->
                        val isCurrent = song.id == currentSongId
                        val isDragging = idx == draggingIndex
                        // Only songs after the one currently playing can be
                        // reordered — reordering something already played, or
                        // the currently-playing song itself, doesn't map to a
                        // sensible "upcoming" position.
                        val canDrag = idx > queueIndex

                        if (idx == queueIndex + 1) {
                            Text(
                                "NEXT UP",
                                color = Color(0xFF6B6080),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    shadowElevation = if (isDragging) 12f else 0f
                                }
                        ) {
                            QueueRow(
                                song = song,
                                isCurrent = isCurrent,
                                showDragHandle = canDrag,
                                isDragging = isDragging,
                                onClick = { onPlaySong(song) },
                                dragHandleModifier = if (!canDrag) Modifier else Modifier.pointerInput(song.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = idx
                                            dragStartIndex = idx
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            if (dragStartIndex != -1 && draggingIndex != dragStartIndex) {
                                                onReorder(dragStartIndex, draggingIndex)
                                            }
                                            draggingIndex = -1
                                            dragStartIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            // Snap the local list back if the gesture was
                                            // interrupted mid-move without a clean end.
                                            items = queue
                                            draggingIndex = -1
                                            dragStartIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val moveBy = (dragOffsetY / rowHeightPx).roundToInt()
                                            if (moveBy != 0) {
                                                val minIndex = queueIndex + 1
                                                val newIndex = (draggingIndex + moveBy)
                                                    .coerceIn(minIndex, items.size - 1)
                                                if (newIndex != draggingIndex) {
                                                    val mutable = items.toMutableList()
                                                    val moved = mutable.removeAt(draggingIndex)
                                                    mutable.add(newIndex, moved)
                                                    items = mutable
                                                    dragOffsetY -= (newIndex - draggingIndex) * rowHeightPx
                                                    draggingIndex = newIndex
                                                }
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    showDragHandle: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    dragHandleModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QUEUE_ROW_HEIGHT)
            .clickable(onClick = onClick)
            .background(if (isDragging) Color(0xFF241E3D) else if (isCurrent) Color(0xFF1F1A35) else Color.Transparent)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1730)),
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

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) SwPurple else Color.White,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
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

        if (isCurrent) {
            Icon(Icons.Filled.MusicNote, contentDescription = "Playing", tint = SwPurple, modifier = Modifier.size(18.dp))
        } else if (showDragHandle) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isDragging) SwPurple else Color(0xFF6B6080),
                modifier = Modifier
                    .size(28.dp)
                    .padding(4.dp)
                    .then(dragHandleModifier)
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }
    }
}
