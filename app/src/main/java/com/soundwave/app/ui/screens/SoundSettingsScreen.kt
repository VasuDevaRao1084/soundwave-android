package com.soundwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundwave.app.audio.AudioEffectsManager
import com.soundwave.app.ui.theme.SwBg
import com.soundwave.app.ui.theme.SwPurple
import com.soundwave.app.ui.theme.SwSurface
import com.soundwave.app.ui.theme.SwSurfaceLight
import com.soundwave.app.ui.theme.SwTextSecondary
import com.soundwave.app.viewmodel.AppViewModel

@Composable
fun SoundSettingsScreen(
    audioQuality: AppViewModel.AudioQuality,
    onSetAudioQuality: (AppViewModel.AudioQuality) -> Unit,
    smoothTransitionsEnabled: Boolean,
    onSetSmoothTransitions: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // These read AudioEffectsManager's current values once; each control below
    // updates both its own local state (for instant UI feedback) and the
    // manager itself (which applies the real Android AudioFX effect).
    var eqEnabled by remember { mutableStateOf(AudioEffectsManager.eqEnabled) }
    var bassBoost by remember { mutableStateOf(AudioEffectsManager.bassBoostStrength.toFloat()) }
    var volumeBoost by remember { mutableStateOf(AudioEffectsManager.volumeBoostMb.toFloat()) }
    var selectedPreset by remember { mutableStateOf(AudioEffectsManager.eqPresetIndex) }
    val presetNames = remember { AudioEffectsManager.getPresetNames() }
    var bands by remember { mutableStateOf(AudioEffectsManager.getBands()) }

    fun noRippleClick(onClick: () -> Unit): Modifier = Modifier.clickable(
        indication = null,
        interactionSource = MutableInteractionSource(),
        onClick = onClick
    )

    Column(modifier = Modifier.fillMaxSize().background(SwBg)) {
        Spacer(Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Sound Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── Quality ──────────────────────────────────────────────────────
            item {
                SectionCard(title = "Streaming Quality") {
                    Text(
                        "Higher quality uses more mobile data.",
                        color = SwTextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppViewModel.AudioQuality.values().forEach { q ->
                            val selected = q == audioQuality
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) SwPurple else SwSurfaceLight, RoundedCornerShape(10.dp))
                                    .then(noRippleClick { onSetAudioQuality(q) })
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(q.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("${q.kbps}kbps", color = if (selected) Color.White.copy(alpha = 0.8f) else SwTextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Smooth transitions ──────────────────────────────────────────
            item {
                SectionCard(title = "Smooth Transitions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fade between songs", color = Color.White, fontSize = 14.sp)
                            Text(
                                "Gently fades out the last few seconds instead of a hard cut.",
                                color = SwTextSecondary, fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = smoothTransitionsEnabled,
                            onCheckedChange = onSetSmoothTransitions,
                            colors = SwitchDefaults.colors(checkedTrackColor = SwPurple)
                        )
                    }
                }
            }

            // ── Equalizer ────────────────────────────────────────────────────
            item {
                SectionCard(title = "Equalizer") {
                    if (!AudioEffectsManager.isEqualizerAvailable()) {
                        Text(
                            "Equalizer isn't supported on this device.",
                            color = SwTextSecondary, fontSize = 13.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enabled", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = eqEnabled,
                                onCheckedChange = {
                                    eqEnabled = it
                                    AudioEffectsManager.setEqEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = SwPurple)
                            )
                        }

                        if (presetNames.isNotEmpty()) {
                            Text("Presets", color = SwTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(presetNames.size) { i ->
                                    val selected = selectedPreset == i
                                    Box(
                                        modifier = Modifier
                                            .background(if (selected) SwPurple else SwSurfaceLight, RoundedCornerShape(20.dp))
                                            .then(noRippleClick {
                                                selectedPreset = i
                                                AudioEffectsManager.setPreset(context, i)
                                                bands = AudioEffectsManager.getBands() // presets change band gains — refresh sliders
                                            })
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(presetNames[i], color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Manual per-band sliders — real Android Equalizer bands,
                        // not fake/decorative. Adjusting any slider switches out
                        // of preset mode into a custom EQ curve.
                        bands.forEach { band ->
                            val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}kHz" else "${band.centerFreqHz}Hz"
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(freqLabel, color = SwTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text("${band.gainMb / 100}dB", color = SwTextSecondary, fontSize = 11.sp)
                                }
                                Slider(
                                    value = band.gainMb.toFloat(),
                                    valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                                    onValueChange = { newVal ->
                                        selectedPreset = -1
                                        AudioEffectsManager.setBandLevel(context, band.index, newVal.toInt())
                                        bands = bands.map { if (it.index == band.index) it.copy(gainMb = newVal.toInt()) else it }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = SwPurple, activeTrackColor = SwPurple)
                                )
                            }
                        }
                    }
                }
            }

            // ── Bass Boost ───────────────────────────────────────────────────
            item {
                SectionCard(title = "Bass Boost") {
                    if (!AudioEffectsManager.isBassBoostAvailable()) {
                        Text("Bass Boost isn't supported on this device.", color = SwTextSecondary, fontSize = 13.sp)
                    } else {
                        Slider(
                            value = bassBoost,
                            valueRange = 0f..1000f,
                            onValueChange = {
                                bassBoost = it
                                AudioEffectsManager.setBassBoost(context, it.toInt())
                            },
                            colors = SliderDefaults.colors(thumbColor = SwPurple, activeTrackColor = SwPurple)
                        )
                        Text(
                            if (bassBoost <= 0f) "Off" else "${(bassBoost / 10).toInt()}%",
                            color = SwTextSecondary, fontSize = 12.sp
                        )
                    }
                }
            }

            // ── Volume Boost ─────────────────────────────────────────────────
            item {
                SectionCard(title = "Volume Boost") {
                    Text(
                        "Boosts quiet tracks. Not the same as matching loudness across songs — just a volume increase.",
                        color = SwTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)
                    )
                    if (!AudioEffectsManager.isVolumeBoostAvailable()) {
                        Text("Not supported on this device.", color = SwTextSecondary, fontSize = 13.sp)
                    } else {
                        Slider(
                            value = volumeBoost,
                            valueRange = 0f..2000f,
                            onValueChange = {
                                volumeBoost = it
                                AudioEffectsManager.setVolumeBoost(context, it.toInt())
                            },
                            colors = SliderDefaults.colors(thumbColor = SwPurple, activeTrackColor = SwPurple)
                        )
                        Text(
                            if (volumeBoost <= 0f) "Off" else "+${(volumeBoost / 100).toInt()}dB",
                            color = SwTextSecondary, fontSize = 12.sp
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(SwSurface, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}
