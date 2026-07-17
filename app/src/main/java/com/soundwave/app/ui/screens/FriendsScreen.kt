package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.soundwave.app.data.FriendProfile
import com.soundwave.app.data.FriendRequestItem
import com.soundwave.app.data.Playlist
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary

private val PurpleGradient = Brush.linearGradient(listOf(Color(0xFF9A7BFF), Color(0xFF5C2FE0)))

@Composable
fun FriendsScreen(
    friendRequests: List<FriendRequestItem>,
    searchResult: FriendProfile?,
    searchStatus: String?,
    friendPlaylists: List<Playlist>,
    actionError: String?,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSendRequest: (FriendProfile) -> Unit,
    onRespond: (String, Boolean) -> Unit,
    onOpenFriendPlaylists: (String) -> Unit,
    onImportPlaylist: (Playlist) -> Unit,
    onClearActionError: () -> Unit,
    onOpenActivity: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var expandedFriendId by remember { mutableStateOf<String?>(null) }

    val accepted = friendRequests.filter { it.status == "accepted" }
    val incoming = friendRequests.filter { it.status == "pending" && it.isIncoming }
    val outgoing = friendRequests.filter { it.status == "pending" && !it.isIncoming }

    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.clickable(onClick = onBack).size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text("Friends", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        actionError?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF3A1620))
                    .clickable { onClearActionError() }
                    .padding(12.dp)
            ) {
                Text(error, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color(0xFFFF8A8A), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            // ── Activity feed entry point ──────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF2A1F52), Color(0xFF1A1730))))
                        .clickable(onClick = onOpenActivity)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SwPurple.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = SwPurple, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Friend Activity", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("See what your friends are playing", color = SwTextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Search by email ───────────────────────────────────────────────
            item {
                Text(
                    "ADD A FRIEND",
                    color = SwTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Friend's email", color = SwTextTertiary) },
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
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SwPurple) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (query.isNotBlank()) onSearch(query.trim()) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SwPurple)
                    ) { Text("Find", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(12.dp))

                when (searchStatus) {
                    "searching" -> Text("Searching…", color = SwTextSecondary, fontSize = 13.sp)
                    "not_found" -> Text("No SoundWave user with that email.", color = SwTextSecondary, fontSize = 13.sp)
                    "self" -> Text("That's your own email.", color = SwTextSecondary, fontSize = 13.sp)
                }

                searchResult?.let { profile ->
                    // A previously DECLINED request shouldn't block sending a new
                    // one — only an actually pending or already-accepted request
                    // should. Without this check, rejecting someone permanently
                    // hid the "Send request" button and made it look stuck.
                    val alreadyRequested = friendRequests.any {
                        it.otherUser.id == profile.id && it.status != "declined"
                    }
                    FriendRow(profile = profile) {
                        if (alreadyRequested) {
                            Text("Pending", color = SwTextTertiary, fontSize = 13.sp)
                        } else {
                            Text(
                                "Send request",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SwPurple)
                                    .clickable { onSendRequest(profile) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Incoming requests ────────────────────────────────────────────
            if (incoming.isNotEmpty()) {
                item {
                    SectionLabel("REQUESTS")
                }
                items(incoming, key = { it.requestId }) { req ->
                    FriendRow(profile = req.otherUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E3A28))
                                    .clickable { onRespond(req.requestId, true) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Accept", tint = Color(0xFF4CD964), modifier = Modifier.size(18.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3A1E1E))
                                    .clickable { onRespond(req.requestId, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Decline", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            if (outgoing.isNotEmpty()) {
                item { SectionLabel("SENT") }
                items(outgoing, key = { it.requestId }) { req ->
                    FriendRow(profile = req.otherUser) {
                        Text("Pending", color = SwTextTertiary, fontSize = 13.sp)
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            // ── Friends ───────────────────────────────────────────────────────
            item { SectionLabel("YOUR FRIENDS") }
            if (accepted.isEmpty()) {
                item {
                    Text(
                        "No friends yet — search their email above to send a request.",
                        color = SwTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            items(accepted, key = { it.requestId }) { req ->
                Column {
                    FriendRow(profile = req.otherUser) {
                        Text(
                            if (expandedFriendId == req.otherUser.id) "Hide" else "View playlists",
                            color = SwPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                if (expandedFriendId == req.otherUser.id) {
                                    expandedFriendId = null
                                } else {
                                    expandedFriendId = req.otherUser.id
                                    onOpenFriendPlaylists(req.otherUser.id)
                                }
                            }
                        )
                    }
                    if (expandedFriendId == req.otherUser.id) {
                        if (friendPlaylists.isEmpty()) {
                            Text(
                                "No playlists shared yet.",
                                color = SwTextTertiary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp)
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(friendPlaylists, key = { it.id }) { pl ->
                                    FriendPlaylistCard(playlist = pl, onImport = { onImportPlaylist(pl) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = SwTextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/** Spotify/Apple-Music-style shared-playlist card: a 2x2 cover-art mosaic
 * (or a single gradient tile with a music note when there aren't enough
 * songs yet), title + song count beneath, and a pill-shaped Import button
 * overlaid on the art itself instead of a plain trailing text link. */
@Composable
private fun FriendPlaylistCard(playlist: Playlist, onImport: () -> Unit) {
    val thumbs = playlist.songs.mapNotNull { it.thumbnail }.distinct().take(4)
    Column(modifier = Modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SwSurface)
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
                Box(
                    modifier = Modifier.fillMaxSize().background(PurpleGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(32.dp))
                }
            }

            // Bottom scrim so the Import pill is always legible over any art.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SwPurple)
                    .clickable(onClick = onImport)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Import", tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.songs.size} songs", color = SwTextSecondary, fontSize = 11.sp)
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
private fun FriendRow(profile: FriendProfile, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SwSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Subtle gradient ring around the avatar (Spotify/Apple-Music-style
        // friend presence treatment) instead of a flat filled circle.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(PurpleGradient)
                .padding(2.dp)
                .clip(CircleShape)
                .background(SwSurface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF23202E)),
                contentAlignment = Alignment.Center
            ) {
                if (profile.avatarUrl != null) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.displayName ?: profile.email, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(profile.email, color = SwTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
