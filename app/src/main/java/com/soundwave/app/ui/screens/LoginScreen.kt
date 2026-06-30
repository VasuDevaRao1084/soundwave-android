package com.soundwave.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPink
import com.soundwave.app.ui.theme.SwPurple

@Composable
fun LoginScreen(isLoading: Boolean, onSignInClick: () -> Unit) {
    // Subtle breathing animation on the logo for a premium, alive feel
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF241A3D), SwBg, SwBg),
                    radius = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Logo mark ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(scale)
                    .shadow(28.dp, RoundedCornerShape(28.dp), spotColor = SwPurple)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(SwPurple, SwPink))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = "SoundWave logo",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "SoundWave",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your music, everywhere.",
                color = Color(0xFF9A8FBF),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(64.dp))

            // ── Sign in button ─────────────────────────────────────────────────
            Button(
                onClick = onSignInClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .height(56.dp)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SwPurple, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoogleLogo()
                        Spacer(Modifier.width(12.dp))
                        Text("Continue with Google", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "By continuing, you agree to our Terms & Privacy Policy",
                color = Color(0xFF4A4560),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GoogleLogo() {
    // Simple 4-color "G" mark built from arcs/shapes so we don't need
    // an external image asset — matches Google's brand colors closely
    // enough for a sign-in button without needing to bundle a PNG/SVG.
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "G",
            color = Color(0xFF4285F4),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}
