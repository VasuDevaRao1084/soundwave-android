package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.AsyncImage
import com.soundwave.app.data.YoutubeApi
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwSurfaceLight
import kotlinx.coroutines.delay

@Composable
fun AlbumSearchScreen(
    savedAlbumIds: Set<String>,
    onBack: () -> Unit,
    onSaveAlbum: (SavedAlbum) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YoutubeApi.AlbumResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(400)
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        loading = true
        results = try { YoutubeApi.searchAlbums(query) } catch (e: Exception) { emptyList() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Find an album", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search albums...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SwSurfaceLight, unfocusedContainerColor = SwSurfaceLight,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(40.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            results.isEmpty() && query.isNotBlank() -> Box(Modifier.fillMaxWidth().padding(40.dp)) {
                Text("No albums found", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
            else -> LazyColumn {
                items(results) { album ->
                    val alreadySaved = savedAlbumIds.contains(album.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !alreadySaved) {
                                onSaveAlbum(
                                    SavedAlbum(album.id, album.name, album.artist, album.thumbnail, album.name)
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = album.thumbnail, contentDescription = null,
                            modifier = Modifier.size(48.dp).background(SwSurfaceLight, RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.name, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(album.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                        }
                        Text(
                            if (alreadySaved) "Saved" else "Save",
                            color = if (alreadySaved) Color.Gray else Color(0xFF8B5CF6),
                            fontSize = 13.sp
                        )
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}
