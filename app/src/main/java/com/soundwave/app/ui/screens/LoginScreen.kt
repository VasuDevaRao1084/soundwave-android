package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPink
import com.soundwave.app.ui.theme.SwPurple

@Composable
fun LoginScreen(isLoading: Boolean, onSignInClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        Brush.linearGradient(listOf(SwPurple, SwPink)),
                        RoundedCornerShape(24.dp)
                    )
            )
            Spacer(Modifier.height(24.dp))
            Text("SoundWave", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Your music, everywhere.", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onSignInClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.height(52.dp).fillMaxWidth(0.8f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SwPurple, strokeWidth = 2.dp)
                } else {
                    Text("Continue with Google", color = Color.Black, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
