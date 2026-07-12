package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.FriendProfile
import com.soundwave.app.data.FriendRequestItem
import com.soundwave.app.data.Playlist
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.ui.theme.SwTextTertiary

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
    onClearActionError: () -> Unit
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
            Text("Friends", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        actionError?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
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
            // ── Search by email ───────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Friend's email") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (query.isNotBlank()) onSearch(query.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = SwPurple)
                    ) { Text("Find") }
                }
                Spacer(Modifier.height(12.dp))

                when (searchStatus) {
                    "searching" -> Text("Searching…", color = SwTextSecondary, fontSize = 13.sp)
                    "not_found" -> Text("No SoundWave user with that email.", color = SwTextSecondary, fontSize = 13.sp)
                    "self" -> Text("That's your own email.", color = SwTextSecondary, fontSize = 13.sp)
                }

                searchResult?.let { profile ->
                    val alreadyRequested = friendRequests.any { it.otherUser.id == profile.id }
                    FriendRow(profile = profile) {
                        if (alreadyRequested) {
                            Text("Pending", color = SwTextTertiary, fontSize = 13.sp)
                        } else {
                            Text(
                                "Send request",
                                color = SwPurple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSendRequest(profile) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Incoming requests ────────────────────────────────────────────
            if (incoming.isNotEmpty()) {
                item {
                    Text("Requests", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                }
                items(incoming, key = { it.requestId }) { req ->
                    FriendRow(profile = req.otherUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Accept",
                                tint = Color(0xFF4CD964),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRespond(req.requestId, true) }
                                    .padding(6.dp)
                            )
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Decline",
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRespond(req.requestId, false) }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (outgoing.isNotEmpty()) {
                item {
                    Text("Sent", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                }
                items(outgoing, key = { it.requestId }) { req ->
                    FriendRow(profile = req.otherUser) {
                        Text("Pending", color = SwTextTertiary, fontSize = 13.sp)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // ── Friends ───────────────────────────────────────────────────────
            item {
                Text("Your friends", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
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
                                modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)
                            )
                        } else {
                            friendPlaylists.forEach { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 56.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SwTextTertiary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${pl.name} · ${pl.songs.size} songs",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "Import",
                                        color = SwPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onImportPlaylist(pl) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(profile: FriendProfile, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF15121F))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(SwPurple.copy(alpha = 0.7f), Color(0xFF6C2FF2).copy(alpha = 0.7f)))),
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
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.displayName ?: profile.email, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(profile.email, color = SwTextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        trailing()
    }
}
