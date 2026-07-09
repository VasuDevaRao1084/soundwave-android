package com.soundwave.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.soundwave.app.data.UserProfile
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwTextSecondary
import java.io.File
import java.io.FileOutputStream

/**
 * Writes the picked image to a brand-new, uniquely-named file rather than
 * overwriting a fixed filename. A new file path means Coil (or any image
 * loader/cache) sees a genuinely different resource — no stale-cache
 * possibility, no manual cache-key juggling needed. The path itself is
 * persisted (see AppViewModel.avatarPath) so it survives app restarts.
 */
fun saveNewAvatarFile(context: android.content.Context, uri: Uri): String {
    val file = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    return file.absolutePath
}

@Composable
fun ProfileScreen(
    user: UserProfile?,
    avatarPath: String?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onAvatarPicked: (String) -> Unit
) {
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            try {
                val newPath = saveNewAvatarFile(context, uri)
                onAvatarPicked(newPath)
            } catch (e: Exception) {
                com.soundwave.app.data.ErrorLog.log(context, "PROFILE", "Avatar save failed: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwBg)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
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
            Text("Profile", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        // ── Avatar + edit badge ──────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SwPurple, Color(0xFF6C2FF2))))
                    .border(3.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val localFile = avatarPath?.let { File(it) }
                when {
                    localFile != null && localFile.exists() -> AsyncImage(
                        model = localFile,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High
                    )
                    user?.avatarUrl != null -> AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High
                    )
                    else -> Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
            // Edit/camera badge — bottom-right of the avatar circle.
            Box(
                modifier = Modifier
                    .offset(x = 46.dp, y = 46.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SwPurple)
                    .border(3.dp, SwBg, CircleShape)
                    .clickable {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            user?.name ?: "SoundWave User",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (user?.email != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                user.email,
                color = SwTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Tap the camera icon to change your photo",
            color = SwTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.weight(1f))

        // ── Sign out ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 40.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1A1730))
                .clickable(onClick = onSignOut)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign out", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
