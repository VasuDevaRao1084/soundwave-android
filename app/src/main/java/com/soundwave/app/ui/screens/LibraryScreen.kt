package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.Playlist
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.SongRow
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary

private val LikedGradient = Brush.linearGradient(listOf(Color(0xFF6C2FF2), Color(0xFFE0417B)))
private val PlaylistTileGradient = Brush.linearGradient(listOf(Color(0xFF9A7BFF), Color(0xFF5C2FE0)))

private val libraryTabs = listOf("Liked", "Playlists", "Albums", "Downloads")

@Composable
fun LibraryScreen(
    likedSongs: List<Song>,
    playlists: List<Playlist>,
    savedAlbums: List<SavedAlbum>,
    downloadedSongs: List<Song>,
    totalDownloadedBytes: Long = 0L,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onLike: (Song) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenAlbum: (SavedAlbum) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onRemoveAlbum: (SavedAlbum) -> Unit,
    onSearchAlbums: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val tab = selectedTab
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var removeAlbumTarget by remember { mutableStateOf<SavedAlbum?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Library",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(16.dp))

        // Custom pill-shaped segmented control instead of the default Material
        // TabRow underline — matches the rest of the app's rounded, filled
        // "chip" visual language instead of looking like a stock Android tab bar.
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .horizontalScrollPill(),
        ) {
            libraryTabs.forEachIndexed { index, label ->
                val selected = tab == index
                Text(
                    label,
                    color = if (selected) Color.White else SwTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) SwPurple else SwSurface)
                        .clickable { onTabChange(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        when (tab) {
            0 -> LikedSongsTab(
                likedSongs = likedSongs,
                currentSongId = currentSongId,
                isAudioPlaying = isAudioPlaying,
                onPlay = onPlay,
                onLike = onLike,
                onAddToPlaylist = onAddToPlaylist
            )
            1 -> PlaylistsTab(
                playlists = playlists,
                onOpenPlaylist = onOpenPlaylist,
                onCreateClick = { showCreateDialog = true },
                onDeleteClick = { deleteTarget = it }
            )
            2 -> AlbumsTab(
                savedAlbums = savedAlbums,
                onOpenAlbum = onOpenAlbum,
                onSearchAlbums = onSearchAlbums,
                onRemoveClick = { removeAlbumTarget = it }
            )
            3 -> DownloadsTab(
                downloadedSongs = downloadedSongs,
                totalDownloadedBytes = totalDownloadedBytes,
                currentSongId = currentSongId,
                isAudioPlaying = isAudioPlaying,
                onPlay = onPlay,
                onAddToPlaylist = onAddToPlaylist
            )
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Playlist name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onCreatePlaylist(name)
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${pl.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeletePlaylist(pl); deleteTarget = null }) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    removeAlbumTarget?.let { album ->
        AlertDialog(
            onDismissRequest = { removeAlbumTarget = null },
            title = { Text("Remove \"${album.name}\"?") },
            text = { Text("It'll be removed from your saved albums.") },
            confirmButton = {
                TextButton(onClick = { onRemoveAlbum(album); removeAlbumTarget = null }) {
                    Text("Remove", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = { TextButton(onClick = { removeAlbumTarget = null }) { Text("Cancel") } }
        )
    }
}

// A simple no-op scroll wrapper placeholder kept minimal on purpose — the tab
// row fits comfortably at 4 items without needing real horizontal scroll,
// this just keeps the modifier chain readable/extensible.
private fun Modifier.horizontalScrollPill(): Modifier = this

@Composable
private fun LikedSongsTab(
    likedSongs: List<Song>,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    onPlay: (Song, List<Song>) -> Unit,
    onLike: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    if (likedSongs.isEmpty()) {
        EmptyState("No liked songs yet — tap the heart on any song to save it here.")
        return
    }
    LazyColumn {
        item {
            // Spotify-style "Liked Songs" hero tile: full-width gradient card
            // instead of a plain list entry, since this is the one collection
            // every user has and it deserves to feel like a real destination.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(LikedGradient)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.weight(1f))
                    Text("Liked Songs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${likedSongs.size} songs", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onPlay(likedSongs.shuffled().first(), likedSongs) }
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle play", tint = Color(0xFF2A1F52), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        items(likedSongs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isPlaying = song.id == currentSongId,
                isCurrentlyPlaying = song.id == currentSongId && isAudioPlaying,
                isLiked = true,
                onClick = { onPlay(song, likedSongs) },
                onLikeClick = { onLike(song) },
                onMoreClick = { onAddToPlaylist(song) }
            )
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    onOpenPlaylist: (Playlist) -> Unit,
    onCreateClick: () -> Unit,
    onDeleteClick: (Playlist) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { NewPlaylistTile(onClick = onCreateClick) }
        items(playlists, key = { it.id }) { pl ->
            PlaylistGridCard(playlist = pl, onClick = { onOpenPlaylist(pl) }, onDelete = { onDeleteClick(pl) })
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun NewPlaylistTile(onClick: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, SwTextTertiary, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SwSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = SwPurple, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("New Playlist", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("Create one", color = SwTextSecondary, fontSize = 11.sp)
    }
}

/** Same cover-art mosaic treatment as the Friends screen's shared-playlist
 * cards, for visual consistency across the app — a 2x2 collage of the
 * playlist's own songs instead of a flat icon tile once it has enough songs. */
@Composable
private fun PlaylistGridCard(playlist: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    val thumbs = playlist.songs.mapNotNull { it.thumbnail }.distinct().take(4)
    var showMenu by remember { mutableStateOf(false) }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SwSurface)
                .clickable(onClick = onClick)
        ) {
            if (thumbs.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        MosaicTile(thumbs[0], Modifier.weight(1f).fillMaxHeight())
                        MosaicTile(thumbs[1], Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f)) {
                        MosaicTile(thumbs[2], Modifier.weight(1f).fillMaxHeight())
                        MosaicTile(thumbs[3], Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else if (thumbs.isNotEmpty()) {
                AsyncImage(
                    model = thumbs.first(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(PlaylistTileGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(32.dp))
                }
            }

            if (playlist.isPrivate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(6.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Private", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }

            // Long-press-free delete: a small always-reachable "×" affordance
            // in the corner keeps the tap target obvious without needing a
            // hidden long-press gesture nobody would discover.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showMenu = true }
                    .padding(6.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${playlist.songs.size} songs" + if (playlist.isPrivate) " · Private" else "",
            color = SwTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showMenu = false }) { Text("Delete", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { showMenu = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MosaicTile(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High
    )
}

@Composable
private fun AlbumsTab(
    savedAlbums: List<SavedAlbum>,
    onOpenAlbum: (SavedAlbum) -> Unit,
    onSearchAlbums: () -> Unit,
    onRemoveClick: (SavedAlbum) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { FindAlbumTile(onClick = onSearchAlbums) }
        items(savedAlbums, key = { it.id }) { album ->
            AlbumGridCard(album = album, onClick = { onOpenAlbum(album) }, onRemove = { onRemoveClick(album) })
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun FindAlbumTile(onClick: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, SwTextTertiary, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SwSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = SwPurple, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Find an Album", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("Search & save", color = SwTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun AlbumGridCard(album: SavedAlbum, onClick: () -> Unit, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SwSurface)
                .clickable(onClick = onClick)
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary, modifier = Modifier.size(32.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showMenu = true }
                    .padding(6.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove album", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(album.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = SwTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Remove \"${album.name}\"?") },
            text = { Text("It'll be removed from your saved albums.") },
            confirmButton = {
                TextButton(onClick = { onRemove(); showMenu = false }) { Text("Remove", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { showMenu = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DownloadsTab(
    downloadedSongs: List<Song>,
    totalDownloadedBytes: Long,
    currentSongId: String?,
    isAudioPlaying: Boolean,
    onPlay: (Song, List<Song>) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    if (downloadedSongs.isEmpty()) {
        EmptyState("No downloaded songs yet — download from any playlist or album to listen offline.")
        return
    }
    LazyColumn {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SwSurface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SwPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = SwPurple, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${downloadedSongs.size} songs downloaded", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${formatBytes(totalDownloadedBytes)} used on this device", color = SwTextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        items(downloadedSongs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isPlaying = song.id == currentSongId,
                isCurrentlyPlaying = song.id == currentSongId && isAudioPlaying,
                isLiked = false,
                onClick = { onPlay(song, downloadedSongs) },
                onMoreClick = { onAddToPlaylist(song) }
            )
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = SwTextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
