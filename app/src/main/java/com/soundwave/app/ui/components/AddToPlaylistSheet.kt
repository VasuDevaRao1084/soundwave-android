package com.soundwave.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.Playlist
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwSurfaceLight

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SwSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Add to playlist", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreate = true }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp).background(SwSurfaceLight, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("New playlist", color = Color.White)
            }

            if (showCreate) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("Playlist name") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (newName.isNotBlank()) { onCreateNew(newName); newName = ""; showCreate = false }
                    }) { Text("Create") }
                }
            }

            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(playlists) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(pl) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).background(SwSurfaceLight, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(pl.name, color = Color.White)
                            Text("${pl.songs.size} songs", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
