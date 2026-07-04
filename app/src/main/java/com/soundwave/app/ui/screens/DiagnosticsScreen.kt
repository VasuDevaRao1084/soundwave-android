package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.data.ErrorLog
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwTextSecondary

@Composable
fun DiagnosticsScreen(
    entries: List<ErrorLog.Entry>,
    onBack: () -> Unit,
    onCopyAll: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "Diagnostics",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopyAll, enabled = entries.isNotEmpty()) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all", tint = Color.White)
            }
            IconButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear log", tint = Color.White)
            }
        }
        Text(
            "Recent errors and crashes — most recent first. Tap the copy icon to grab everything for debugging.",
            color = SwTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No errors logged — all clear.", color = SwTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(entries) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(SwSurface, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(entry.tag, color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(entry.time, color = SwTextSecondary, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(entry.message, color = Color.White, fontSize = 13.sp)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
