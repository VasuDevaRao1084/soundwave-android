package com.soundwave.app.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import org.json.JSONObject

/**
 * Wraps Android's real, built-in audio effects (android.media.audiofx) —
 * Equalizer, BassBoost, and LoudnessEnhancer. These are genuine on-device DSP
 * effects, not a fake/placebo UI: they run against the actual audio session
 * ExoPlayer is outputting through.
 *
 * IMPORTANT about scope: LoudnessEnhancer here is exposed as a "Volume Boost"
 * — a single adjustable gain increase. It is NOT true cross-track loudness
 * normalization (matching perceived loudness between different songs), which
 * would require per-track loudness metadata (like ReplayGain/LUFS values)
 * that JioSaavn's API doesn't provide. Calling it "normalization" would be
 * overclaiming what this actually does, so it's deliberately labeled as a
 * boost/leveler instead.
 *
 * Every effect is wrapped individually in try/catch: some devices/OEMs don't
 * support every effect, and a failure to initialize one (e.g. BassBoost)
 * must never prevent the others from working or affect playback itself.
 */
object AudioEffectsManager {
    private const val PREFS = "soundwave_audio_effects"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = -1

    data class Band(val index: Int, val minMb: Int, val maxMb: Int, val centerFreqHz: Int, val gainMb: Int)

    // In-memory cache of user settings, kept in sync with SharedPreferences so
    // the UI can read current values without hitting disk on every frame.
    var eqEnabled: Boolean = true
        private set
    var eqPresetIndex: Int = -1 // -1 means "custom" (manual band gains below)
        private set
    var bassBoostStrength: Int = 0 // 0–1000, per Android's BassBoost API
        private set
    var volumeBoostMb: Int = 0 // 0–2000 millibels, per LoudnessEnhancer API
        private set
    private var customBandGains: MutableMap<Int, Int> = mutableMapOf()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            eqEnabled = prefs.getBoolean("eqEnabled", true)
            eqPresetIndex = prefs.getInt("presetIndex", -1)
            bassBoostStrength = prefs.getInt("bassBoost", 0)
            volumeBoostMb = prefs.getInt("volumeBoost", 0)
            prefs.getString("customBands", null)?.let { raw ->
                val obj = JSONObject(raw)
                obj.keys().forEach { k -> customBandGains[k.toInt()] = obj.getInt(k) }
            }
        } catch (e: Exception) {
            // Fall back to defaults — never let a corrupt prefs value break startup
        }
    }

    /** Call whenever ExoPlayer's audio session ID becomes available/changes. */
    fun attachToSession(context: Context, sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        releaseEffects()
        currentSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId)
            equalizer?.enabled = eqEnabled
            applyStoredEqSettings()
        } catch (e: Exception) {
            android.util.Log.e("SoundWave", "Equalizer unavailable on this device", e)
            equalizer = null
        }

        try {
            bassBoost = BassBoost(0, sessionId)
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bassBoostStrength.toShort())
            }
            bassBoost?.enabled = bassBoostStrength > 0
        } catch (e: Exception) {
            android.util.Log.e("SoundWave", "BassBoost unavailable on this device", e)
            bassBoost = null
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId)
            loudnessEnhancer?.setTargetGain(volumeBoostMb)
            loudnessEnhancer?.enabled = volumeBoostMb > 0
        } catch (e: Exception) {
            android.util.Log.e("SoundWave", "LoudnessEnhancer unavailable on this device", e)
            loudnessEnhancer = null
        }
    }

    private fun applyStoredEqSettings() {
        val eq = equalizer ?: return
        try {
            if (eqPresetIndex in 0 until eq.numberOfPresets) {
                eq.usePreset(eqPresetIndex.toShort())
            } else {
                customBandGains.forEach { (band, gain) ->
                    try { eq.setBandLevel(band.toShort(), gain.toShort()) } catch (e: Exception) { /* skip this band */ }
                }
            }
        } catch (e: Exception) { /* leave EQ at its default flat state */ }
    }

    /** Built-in device/vendor presets (Normal, Rock, Jazz, etc.) — real Android AudioFX presets, not hardcoded by us. */
    fun getPresetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return try { (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) } } catch (e: Exception) { emptyList() }
    }

    fun getBands(): List<Band> {
        val eq = equalizer ?: return emptyList()
        return try {
            val range = eq.bandLevelRange
            (0 until eq.numberOfBands).map { i ->
                Band(
                    index = i,
                    minMb = range[0].toInt(),
                    maxMb = range[1].toInt(),
                    centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000,
                    gainMb = eq.getBandLevel(i.toShort()).toInt()
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun isEqualizerAvailable(): Boolean = equalizer != null
    fun isBassBoostAvailable(): Boolean = bassBoost?.strengthSupported == true
    fun isVolumeBoostAvailable(): Boolean = loudnessEnhancer != null

    fun setEqEnabled(context: Context, enabled: Boolean) {
        eqEnabled = enabled
        try { equalizer?.enabled = enabled } catch (e: Exception) { }
        persist(context)
    }

    fun setPreset(context: Context, index: Int) {
        eqPresetIndex = index
        try { equalizer?.usePreset(index.toShort()) } catch (e: Exception) { }
        persist(context)
    }

    fun setBandLevel(context: Context, band: Int, gainMb: Int) {
        eqPresetIndex = -1 // manual adjustment switches out of preset mode
        customBandGains[band] = gainMb
        try { equalizer?.setBandLevel(band.toShort(), gainMb.toShort()) } catch (e: Exception) { }
        persist(context)
    }

    fun setBassBoost(context: Context, strength: Int) {
        bassBoostStrength = strength
        try {
            bassBoost?.enabled = strength > 0
            bassBoost?.setStrength(strength.toShort())
        } catch (e: Exception) { }
        persist(context)
    }

    fun setVolumeBoost(context: Context, gainMb: Int) {
        volumeBoostMb = gainMb
        try {
            loudnessEnhancer?.enabled = gainMb > 0
            loudnessEnhancer?.setTargetGain(gainMb)
        } catch (e: Exception) { }
        persist(context)
    }

    private fun persist(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bandsJson = JSONObject().apply { customBandGains.forEach { (k, v) -> put(k.toString(), v) } }
            prefs.edit()
                .putBoolean("eqEnabled", eqEnabled)
                .putInt("presetIndex", eqPresetIndex)
                .putInt("bassBoost", bassBoostStrength)
                .putInt("volumeBoost", volumeBoostMb)
                .putString("customBands", bandsJson.toString())
                .apply()
        } catch (e: Exception) { }
    }

    fun releaseEffects() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { bassBoost?.release() } catch (e: Exception) { }
        try { loudnessEnhancer?.release() } catch (e: Exception) { }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        currentSessionId = -1
    }
}
